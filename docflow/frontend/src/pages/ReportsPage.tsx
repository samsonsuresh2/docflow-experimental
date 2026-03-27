import { useCallback, useEffect, useMemo, useState, type ChangeEvent } from 'react';
import ReportResultsGrid from '../components/ReportResultsGrid';
import {
  fetchAllReportRows,
  fetchExecutableReportTemplate,
  fetchExecutableReportTemplates,
  runReportTemplate,
} from '../lib/reports';
import type {
  ExecutableReportFilterField,
  ExecutableReportTemplateDetail,
  ExecutableReportTemplateSummary,
  ReportExecutionRunRequest,
  ReportRunResponse,
} from '../types/reports';
import { useUser } from '../lib/UserContext';

type FilterState = Record<
  string,
  {
    op: string;
    value: string;
    valueFrom?: string;
    valueTo?: string;
    mode?: 'MANUAL' | 'PRESET';
    fromValue?: string;
    toValue?: string;
    presetCode?: string;
  }
>;

const OPERATOR_LABELS: Record<string, string> = {
  EQ: '=',
  LIKE: 'Contains',
  LT: '<',
  GT: '>',
  RANGE: 'Range',
  BETWEEN: 'Between',
};

function requiresRangeValues(op: string): boolean {
  return op === 'RANGE' || op === 'BETWEEN';
}

function normaliseError(error: unknown): string {
  if (typeof error === 'string') {
    return error;
  }
  if (error && typeof error === 'object') {
    const maybeResponse = (error as { response?: { data?: unknown; statusText?: string } }).response;
    if (maybeResponse?.data && typeof maybeResponse.data === 'object') {
      const maybeMessage = (maybeResponse.data as { message?: unknown }).message;
      if (typeof maybeMessage === 'string') {
        return maybeMessage;
      }
    }
    if (typeof maybeResponse?.statusText === 'string' && maybeResponse.statusText.trim()) {
      return maybeResponse.statusText;
    }
  }
  return 'Something went wrong while communicating with the reports service.';
}

function defaultOperator(field: ExecutableReportFilterField | undefined): string {
  if (!field) {
    return 'EQ';
  }
  if (Array.isArray(field.allowedOps) && field.allowedOps.length > 0) {
    return field.allowedOps[0];
  }
  return 'EQ';
}

export default function ReportsPage() {
  const { user } = useUser();
  const [templates, setTemplates] = useState<ExecutableReportTemplateSummary[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState<boolean>(true);
  const [templatesError, setTemplatesError] = useState<string | null>(null);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);

  const [templateDetail, setTemplateDetail] = useState<ExecutableReportTemplateDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState<boolean>(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  const [filtersState, setFiltersState] = useState<FilterState>({});

  const [result, setResult] = useState<ReportRunResponse | null>(null);
  const [running, setRunning] = useState<boolean>(false);
  const [runError, setRunError] = useState<string | null>(null);
  const [hasRun, setHasRun] = useState<boolean>(false);
  const [page, setPage] = useState<number>(0);
  const [pageSize, setPageSize] = useState<number>(25);
  const [lastRequest, setLastRequest] = useState<ReportExecutionRunRequest | null>(null);

  useEffect(() => {
    let cancelled = false;
    setTemplatesLoading(true);
    setTemplatesError(null);
    fetchExecutableReportTemplates()
      .then((loaded) => {
        if (!cancelled) {
          setTemplates(loaded);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setTemplatesError(normaliseError(error));
          setTemplates([]);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setTemplatesLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [user?.role]);

  useEffect(() => {
    if (selectedTemplateId == null) {
      setTemplateDetail(null);
      setFiltersState({});
      setDetailError(null);
      setResult(null);
      setHasRun(false);
      setRunError(null);
      return;
    }
    let cancelled = false;
    setDetailLoading(true);
    setDetailError(null);
    fetchExecutableReportTemplate(selectedTemplateId)
      .then((detail) => {
        if (!cancelled) {
          setTemplateDetail(detail);
          const initialFilters: FilterState = {};
          detail.filters.forEach((filter) => {
            const presetMode = filter.type === 'DATE' && filter.presetEnabled;
            initialFilters[filter.key] = {
              op: defaultOperator(filter),
              value: '',
              valueFrom: '',
              valueTo: '',
              mode: presetMode ? 'PRESET' : 'MANUAL',
              presetCode: presetMode ? (filter.presets?.[0]?.code ?? '') : undefined,
            };
          });
          setFiltersState(initialFilters);
          setResult(null);
          setHasRun(false);
          setRunError(null);
          setPage(0);
          setLastRequest(null);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setTemplateDetail(null);
          setFiltersState({});
          setDetailError(normaliseError(error));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setDetailLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [selectedTemplateId]);

  const selectedTemplateName = useMemo(() => {
    if (!selectedTemplateId) {
      return '';
    }
    const template = templates.find((item) => item.id === selectedTemplateId);
    return template?.name ?? '';
  }, [templates, selectedTemplateId]);

  const handleTemplateChange = (event: ChangeEvent<HTMLSelectElement>) => {
    const value = event.target.value;
    if (!value) {
      setSelectedTemplateId(null);
      return;
    }
    const parsed = Number(value);
    if (Number.isNaN(parsed)) {
      setSelectedTemplateId(null);
      return;
    }
    setSelectedTemplateId(parsed);
  };

  const handleFilterValueChange = (key: string, value: string) => {
    setFiltersState((prev) => {
      const current = prev[key] ?? { op: defaultOperator(templateDetail?.filters.find((f) => f.key === key)), value: '' };
      return { ...prev, [key]: { ...current, value } };
    });
  };

  const handleRangeValueChange = (key: string, patch: Partial<{ valueFrom: string; valueTo: string }>) => {
    setFiltersState((prev) => {
      const current = prev[key] ?? { op: defaultOperator(templateDetail?.filters.find((f) => f.key === key)), value: '' };
      return { ...prev, [key]: { ...current, ...patch } };
    });
  };

  const handleDateModeChange = (key: string, mode: 'MANUAL' | 'PRESET') => {
    setFiltersState((prev) => {
      const field = templateDetail?.filters.find((f) => f.key === key);
      const current = prev[key] ?? { op: defaultOperator(field), value: '' };
      return {
        ...prev,
        [key]: {
          ...current,
          mode,
          presetCode: mode === 'PRESET' ? current.presetCode ?? field?.presets?.[0]?.code ?? '' : current.presetCode,
        },
      };
    });
  };

  const handleDateRangeValueChange = (key: string, patch: Partial<{ fromValue: string; toValue: string; presetCode: string }>) => {
    setFiltersState((prev) => {
      const current = prev[key] ?? { op: defaultOperator(templateDetail?.filters.find((f) => f.key === key)), value: '' };
      return { ...prev, [key]: { ...current, ...patch } };
    });
  };

  const handleFilterOpChange = (key: string, op: string) => {
    setFiltersState((prev) => {
      const current = prev[key] ?? { op: defaultOperator(templateDetail?.filters.find((f) => f.key === key)), value: '' };
      if (requiresRangeValues(op)) {
        return { ...prev, [key]: { ...current, op, value: '' } };
      }
      return { ...prev, [key]: { ...current, op, valueFrom: '', valueTo: '' } };
    });
  };

  const buildRunRequest = useCallback((): ReportExecutionRunRequest | null => {
    if (!templateDetail) {
      return null;
    }
    const filters = templateDetail.filters
      .map((filter) => {
        const state = filtersState[filter.key] ?? { op: defaultOperator(filter), value: '' };
        if (filter.type === 'DATE' && filter.presetEnabled) {
          const mode = state.mode ?? 'PRESET';
          if (mode === 'PRESET') {
            if (!state.presetCode) {
              return null;
            }
            return { key: filter.key, mode, presetCode: state.presetCode };
          }
          const op = state.op || defaultOperator(filter);
          if (requiresRangeValues(op)) {
            const valueFrom = (state.valueFrom ?? state.fromValue ?? '').trim();
            const valueTo = (state.valueTo ?? state.toValue ?? '').trim();
            if (!valueFrom && !valueTo) {
              return null;
            }
            return { key: filter.key, mode, op, valueFrom, valueTo };
          }
          const value = state.value?.trim() ?? '';
          if (!value) {
            return null;
          }
          return { key: filter.key, mode, op, value };
        }

        const op = state.op || defaultOperator(filter);
        if (requiresRangeValues(op)) {
          const valueFrom = state.valueFrom?.trim() ?? '';
          const valueTo = state.valueTo?.trim() ?? '';
          if (!valueFrom && !valueTo) {
            return null;
          }
          return { key: filter.key, op, valueFrom, valueTo };
        }
        const value = state.value?.trim() ?? '';
        if (!value) {
          return null;
        }
        return { key: filter.key, op, value };
      })
      .filter((item): item is NonNullable<typeof item> => Boolean(item));

    return {
      templateId: templateDetail.templateId,
      filters,
    };
  }, [templateDetail, filtersState]);

  const executeRun = useCallback(
    async (request: ReportExecutionRunRequest, targetPage: number, targetSize: number) => {
      setRunning(true);
      setRunError(null);
      try {
        const response = await runReportTemplate(request, targetPage, targetSize);
        setResult(response);
        setHasRun(true);
      } catch (error) {
        setResult(null);
        setHasRun(true);
        setRunError(normaliseError(error));
      } finally {
        setRunning(false);
      }
    },
    [],
  );

  const handleGenerate = async () => {
    const request = buildRunRequest();
    if (!request) {
      setRunError('Choose a report template to run.');
      setHasRun(false);
      return;
    }
    setPage(0);
    setLastRequest(request);
    await executeRun(request, 0, pageSize);
  };

  const handlePageChange = async (nextPage: number) => {
    if (!lastRequest) {
      return;
    }
    setPage(nextPage);
    await executeRun(lastRequest, nextPage, pageSize);
  };

  const handlePageSizeChange = async (nextSize: number) => {
    if (nextSize <= 0) {
      return;
    }
    setPageSize(nextSize);
    setPage(0);
    if (!lastRequest) {
      return;
    }
    await executeRun(lastRequest, 0, nextSize);
  };

  const handleFetchAllRows = useCallback(async () => {
    if (!lastRequest) {
      return { columns: result?.columns ?? [], rows: result?.rows ?? [], rowCount: result?.rowCount };
    }
    return fetchAllReportRows((nextPage, nextSize) => runReportTemplate(lastRequest, nextPage, nextSize));
  }, [lastRequest, result]);

  const canGenerate = Boolean(templateDetail && !detailLoading);
  const canGoNext = Boolean(result && typeof result.rowCount === 'number' && (page + 1) * pageSize < result.rowCount);

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">Reports</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">
          Choose a predefined report template, supply filter values, and generate the results. Templates are curated by administrators to
          keep definitions consistent.
        </p>
      </header>

      <section className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <div className="space-y-2">
          <label className="block text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
            Select Report
          </label>
          <select
            value={selectedTemplateId == null ? '' : String(selectedTemplateId)}
            onChange={handleTemplateChange}
            className="w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
            disabled={templatesLoading}
          >
            <option value="">{templatesLoading ? 'Loading templates…' : 'Select a template…'}</option>
            {templates.map((template) => (
              <option key={template.id} value={String(template.id)}>
                {template.name}
              </option>
            ))}
          </select>
          {templatesError ? (
            <p className="text-sm text-red-600 dark:text-red-400">{templatesError}</p>
          ) : selectedTemplateName ? (
            <p className="text-xs text-slate-600 dark:text-slate-400">
              {selectedTemplateName}
              {templateDetail?.filters?.length ? ` • ${templateDetail.filters.length} filter${templateDetail.filters.length === 1 ? '' : 's'}` : ''}
            </p>
          ) : (
            <p className="text-xs text-slate-500 dark:text-slate-400">Only administrator-approved templates are listed.</p>
          )}
          {detailError ? <p className="text-sm text-red-600 dark:text-red-400">{detailError}</p> : null}
        </div>
      </section>

      <section className="space-y-4 rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Filters</h2>
            <p className="text-sm text-slate-600 dark:text-slate-300">
              Provide values for the filters defined by the selected template. Leave a value blank to ignore that filter when generating.
            </p>
          </div>
          <button
            type="button"
            className="inline-flex items-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300 dark:bg-blue-500 dark:hover:bg-blue-400"
            onClick={handleGenerate}
            disabled={!canGenerate || running || Boolean(detailError)}
          >
            {running ? 'Generating…' : 'Generate'}
          </button>
        </div>

        {detailLoading ? (
          <p className="text-sm text-slate-500 dark:text-slate-400">Loading template details…</p>
        ) : !templateDetail ? (
          <p className="text-sm text-slate-500 dark:text-slate-400">Select a template to view available filters.</p>
        ) : templateDetail.filters.length === 0 ? (
          <p className="text-sm text-slate-500 dark:text-slate-400">This template has no editable filters. Click Generate to run it.</p>
        ) : (
          <div className="space-y-3">
            {templateDetail.filters.map((filter) => {
              const state = filtersState[filter.key] ?? { op: defaultOperator(filter), value: '' };
              const supportsPreset = filter.type === 'DATE' && filter.presetEnabled;
              const isPresetMode = supportsPreset && (state.mode ?? 'PRESET') === 'PRESET';
              const showOperator = !supportsPreset || !isPresetMode;
              const usesRangeValues = showOperator && requiresRangeValues(state.op);
              return (
                <div
                  key={filter.key}
                  className="grid items-center gap-3 rounded border border-slate-200 px-4 py-3 transition-colors dark:border-slate-700 md:grid-cols-[minmax(220px,1.2fr)_minmax(0,3fr)]"
                >
                  <div className="min-w-0">
                    <div className="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">
                      {filter.label}
                      {filter.label !== filter.key ? (
                        <span className="ml-2 text-xs font-normal uppercase tracking-wide text-slate-500 dark:text-slate-300">{filter.key}</span>
                      ) : null}
                    </div>
                  </div>
                  <div className="flex min-w-0 flex-wrap items-center gap-2 sm:flex-nowrap">
                    {showOperator ? (
                      <div className="w-full sm:w-36">
                        <label className="sr-only" htmlFor={`op-${filter.key}`}>
                          Operator
                        </label>
                        <select
                          id={`op-${filter.key}`}
                          value={state.op}
                          onChange={(event) => handleFilterOpChange(filter.key, event.target.value)}
                          className="w-full rounded border border-slate-300 px-2 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                        >
                          {filter.allowedOps.map((op) => (
                            <option key={`${filter.key}-${op}`} value={op}>
                              {OPERATOR_LABELS[op] ?? op.toUpperCase()}
                            </option>
                          ))}
                        </select>
                      </div>
                    ) : null}
                    {isPresetMode ? (
                      <div className="min-w-[220px] flex-1">
                        <label className="sr-only" htmlFor={`preset-${filter.key}`}>
                          Preset
                        </label>
                        <select
                          id={`preset-${filter.key}`}
                            value={state.presetCode ?? ''}
                            onChange={(event) => handleDateRangeValueChange(filter.key, { presetCode: event.target.value })}
                            className="w-full rounded border border-slate-300 px-2 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                          >
                            <option value="">Select preset…</option>
                            {(filter.presets ?? []).map((preset) => (
                              <option key={`${filter.key}-${preset.code}`} value={preset.code}>
                                {preset.name}
                              </option>
                            ))}
                          </select>
                      </div>
                    ) : usesRangeValues ? (
                            <div className="grid min-w-[260px] flex-1 gap-2 sm:grid-cols-2">
                              <input
                                id={`value-from-${filter.key}`}
                                type={filter.type === 'NUMBER' ? 'number' : 'date'}
                                inputMode={filter.type === 'NUMBER' ? 'decimal' : undefined}
                                value={supportsPreset ? (state.valueFrom ?? state.fromValue ?? '') : (state.valueFrom ?? '')}
                                onChange={(event) =>
                                  supportsPreset
                                    ? handleDateRangeValueChange(filter.key, { valueFrom: event.target.value, fromValue: event.target.value })
                                    : handleRangeValueChange(filter.key, { valueFrom: event.target.value })
                                }
                                placeholder={filter.type === 'NUMBER' ? 'Min' : 'From'}
                                className="w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                              />
                              <input
                                id={`value-to-${filter.key}`}
                                type={filter.type === 'NUMBER' ? 'number' : 'date'}
                                inputMode={filter.type === 'NUMBER' ? 'decimal' : undefined}
                                value={supportsPreset ? (state.valueTo ?? state.toValue ?? '') : (state.valueTo ?? '')}
                                onChange={(event) =>
                                  supportsPreset
                                    ? handleDateRangeValueChange(filter.key, { valueTo: event.target.value, toValue: event.target.value })
                                    : handleRangeValueChange(filter.key, { valueTo: event.target.value })
                                }
                                placeholder={filter.type === 'NUMBER' ? 'Max' : 'To'}
                                className="w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                              />
                            </div>
                          ) : (
                            <div className="min-w-[220px] flex-1">
                              <input
                                id={`value-${filter.key}`}
                                type={filter.type === 'NUMBER' ? 'number' : filter.type === 'DATE' ? 'date' : 'text'}
                                inputMode={filter.type === 'NUMBER' ? 'decimal' : undefined}
                                value={state.value}
                                onChange={(event) => handleFilterValueChange(filter.key, event.target.value)}
                                placeholder={filter.type === 'DATE' ? filter.dateFormat ?? 'YYYY-MM-DD' : ''}
                                className="w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                              />
                            </div>
                    )}
                    {supportsPreset ? (
                      <label className="inline-flex shrink-0 items-center gap-2 rounded border border-slate-300 px-3 py-2 text-sm text-slate-700 transition-colors dark:border-slate-600 dark:text-slate-200 sm:ml-auto">
                        <input
                          type="checkbox"
                          checked={isPresetMode}
                          onChange={(event) => handleDateModeChange(filter.key, event.target.checked ? 'PRESET' : 'MANUAL')}
                          className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 dark:border-slate-500 dark:bg-slate-900"
                        />
                        <span>Preset</span>
                      </label>
                    ) : null}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>

      <ReportResultsGrid
        columns={result?.columns ?? []}
        rows={result?.rows ?? []}
        loading={running}
        error={runError}
        hasRun={hasRun}
        page={page}
        pageSize={pageSize}
        totalRows={result?.rowCount}
        canGoNext={canGoNext}
        onPageChange={handlePageChange}
        onPageSizeChange={handlePageSizeChange}
        fetchAllRows={handleFetchAllRows}
      />
    </div>
  );
}
