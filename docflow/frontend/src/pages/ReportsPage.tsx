import { useCallback, useEffect, useMemo, useState, type ChangeEvent } from 'react';
import ReportResultsGrid from '../components/ReportResultsGrid';
import {
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
  { op: string; value: string; mode?: 'MANUAL' | 'PRESET'; fromValue?: string; toValue?: string; presetCode?: string }
>;

const OPERATOR_LABELS: Record<string, string> = { EQ: '=', LIKE: 'Contains', LT: '<', GT: '>' };

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
            initialFilters[filter.key] = {
              op: defaultOperator(filter),
              value: '',
              mode: filter.type === 'DATE' && filter.presetEnabled ? 'PRESET' : 'MANUAL',
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

  const handleDateModeChange = (key: string, mode: 'MANUAL' | 'PRESET') => {
    setFiltersState((prev) => {
      const current = prev[key] ?? { op: defaultOperator(templateDetail?.filters.find((f) => f.key === key)), value: '' };
      return { ...prev, [key]: { ...current, mode } };
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
      return { ...prev, [key]: { ...current, op } };
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
          const fromValue = state.fromValue?.trim() ?? '';
          const toValue = state.toValue?.trim() ?? '';
          if (!fromValue && !toValue) {
            return null;
          }
          return { key: filter.key, mode, fromValue, toValue };
        }

        const op = state.op || defaultOperator(filter);
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

  const canGenerate = Boolean(templateDetail && !detailLoading);
  const canGoNext = Boolean(result && result.rows.length === pageSize);

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
              const hint =
                filter.type === 'DATE' && filter.dateFormat
                  ? `Format: ${filter.dateFormat}`
                  : filter.type === 'NUMBER'
                  ? 'Number'
                  : undefined;
              return (
                <div
                  key={filter.key}
                  className="grid gap-3 rounded border border-slate-200 p-4 transition-colors dark:border-slate-700 md:grid-cols-[1.5fr_0.7fr_2fr]"
                >
                  <div className="space-y-1">
                    <div className="text-sm font-semibold text-slate-800 dark:text-slate-100">{filter.label}</div>
                    <div className="text-xs uppercase tracking-wide text-slate-500 dark:text-slate-300">{filter.key}</div>
                  </div>
                  <div>
                    <label className="sr-only" htmlFor={`op-${filter.key}`}>
                      Operator
                    </label>
                    <select
                      id={`op-${filter.key}`}
                      value={state.op}
                      onChange={(event) => handleFilterOpChange(filter.key, event.target.value)}
                      disabled={filter.type === 'DATE' && filter.presetEnabled && state.mode === 'PRESET'}
                      className="w-full rounded border border-slate-300 px-2 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                    >
                      {filter.allowedOps.map((op) => (
                        <option key={`${filter.key}-${op}`} value={op}>
                          {OPERATOR_LABELS[op] ?? op.toUpperCase()}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="space-y-1">
                    <label className="sr-only" htmlFor={`value-${filter.key}`}>
                      Value
                    </label>
                    {filter.type === 'DATE' && filter.presetEnabled ? (
                      <div className="space-y-2">
                        <select
                          value={state.mode ?? 'PRESET'}
                          onChange={(event) => handleDateModeChange(filter.key, event.target.value as 'MANUAL' | 'PRESET')}
                          className="w-full rounded border border-slate-300 px-2 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                        >
                          <option value="PRESET">Preset</option>
                          <option value="MANUAL">Manual</option>
                        </select>
                        {(state.mode ?? 'PRESET') === 'PRESET' ? (
                          <select
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
                        ) : (
                          <div className="grid grid-cols-2 gap-2">
                            <input
                              type="date"
                              value={state.fromValue ?? ''}
                              onChange={(event) => handleDateRangeValueChange(filter.key, { fromValue: event.target.value })}
                              className="w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                            />
                            <input
                              type="date"
                              value={state.toValue ?? ''}
                              onChange={(event) => handleDateRangeValueChange(filter.key, { toValue: event.target.value })}
                              className="w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                            />
                          </div>
                        )}
                      </div>
                    ) : (
                      <input
                        id={`value-${filter.key}`}
                        type={filter.type === 'NUMBER' ? 'number' : filter.type === 'DATE' ? 'date' : 'text'}
                        inputMode={filter.type === 'NUMBER' ? 'decimal' : undefined}
                        value={state.value}
                        onChange={(event) => handleFilterValueChange(filter.key, event.target.value)}
                        placeholder={filter.type === 'DATE' ? filter.dateFormat ?? 'YYYY-MM-DD' : ''}
                        className="w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                      />
                    )}
                    {hint ? <p className="text-xs text-slate-500 dark:text-slate-400">{hint}</p> : null}
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
        canGoNext={canGoNext}
        onPageChange={handlePageChange}
        onPageSizeChange={handlePageSizeChange}
      />
    </div>
  );
}
