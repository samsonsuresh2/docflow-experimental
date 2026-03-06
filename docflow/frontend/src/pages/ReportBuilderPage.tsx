import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react';
import ReportResultsGrid from '../components/ReportResultsGrid';
import {
  fetchReportScope,
  fetchReportTemplates,
  runDynamicReport,
  saveReportTemplate,
  updateReportTemplate,
} from '../lib/reports';
import type { DynamicReportRequest, ReportBaseEntity, ReportRunResponse, ReportTemplate } from '../types/reports';

const OPERATORS = ['EQ', 'LIKE', 'LT', 'GT'] as const;
type Operator = (typeof OPERATORS)[number];
const LOGICAL_TYPES = ['STRING', 'NUMBER', 'DATE'] as const;
type LogicalType = (typeof LOGICAL_TYPES)[number];

type FilterRow = {
  id: number;
  key: string;
  op: Operator;
  value: string;
  logicalType: LogicalType;
};

function allowedOperators(type: LogicalType): Operator[] {
  if (type === 'STRING') {
    return ['EQ', 'LIKE'];
  }
  return ['EQ', 'LT', 'GT'];
}

function normalizeOperatorForBackend(op: string): Operator {
  const upper = op.toUpperCase();
  if (upper === '=' || upper === 'EQ') return 'EQ';
  if (upper === '<' || upper === 'LT') return 'LT';
  if (upper === '>' || upper === 'GT') return 'GT';
  return 'EQ';
}

type ColumnOption = {
  value: string;
  label: string;
  group: 'Base Table' | 'Document' | 'Metadata';
};

function normaliseError(error: unknown): string {
  if (typeof error === 'string') {
    return error;
  }
  if (error && typeof error === 'object') {
    const maybeResponse = (error as {
      response?: { data?: { message?: unknown; code?: unknown }; statusText?: string };
    }).response;
    if (maybeResponse?.data && typeof maybeResponse.data === 'object') {
      const maybeCode = (maybeResponse.data as { code?: unknown }).code;
      if (maybeCode === 'DUPLICATE_TEMPLATE_NAME') {
        return 'Duplicate report template name not allowed.';
      }
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

function normalizeKeyForBackend(value: string): string {
  if (!value) {
    return value;
  }
  if (value.startsWith('meta.')) {
    return `meta:${value.slice('meta.'.length)}`;
  }
  return value;
}

function normaliseTemplateKey(value: string): string {
  if (!value) {
    return value;
  }
  if (value.startsWith('meta:')) {
    return `meta.${value.slice('meta:'.length)}`;
  }
  return value;
}

export default function ReportBuilderPage() {
  const [entities, setEntities] = useState<ReportBaseEntity[]>([]);
  const [entityLoading, setEntityLoading] = useState<boolean>(true);
  const [entityError, setEntityError] = useState<string | null>(null);

  const [selectedEntity, setSelectedEntity] = useState<string>('');
  const [entityColumns, setEntityColumns] = useState<Record<string, string[]>>({});
  const [documentColumns, setDocumentColumns] = useState<string[]>([]);
  const [metadataKeys, setMetadataKeys] = useState<string[]>([]);
  const [metadataLoading, setMetadataLoading] = useState<boolean>(false);
  const [metadataError, setMetadataError] = useState<string | null>(null);

  const [selectedColumns, setSelectedColumns] = useState<string[]>([]);
  const [filters, setFilters] = useState<FilterRow[]>([]);

  const [page, setPage] = useState<number>(0);
  const [pageSize, setPageSize] = useState<number>(25);
  const [hasRun, setHasRun] = useState<boolean>(false);
  const [running, setRunning] = useState<boolean>(false);
  const [runError, setRunError] = useState<string | null>(null);
  const [result, setResult] = useState<ReportRunResponse | null>(null);
  const [lastRequest, setLastRequest] = useState<DynamicReportRequest | null>(null);

  const [templates, setTemplates] = useState<ReportTemplate[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState<boolean>(true);
  const [templatesError, setTemplatesError] = useState<string | null>(null);
  const [templateName, setTemplateName] = useState<string>('');
  const [templateSaveError, setTemplateSaveError] = useState<string | null>(null);
  const [templateSaveSuccess, setTemplateSaveSuccess] = useState<string | null>(null);
  const [savingTemplate, setSavingTemplate] = useState<boolean>(false);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
  const [pendingTemplate, setPendingTemplate] = useState<ReportTemplate | null>(null);
  const [loadedTemplate, setLoadedTemplate] = useState<ReportTemplate | null>(null);

  const filterIdRef = useRef<number>(0);

  useEffect(() => {
    let cancelled = false;
    setEntityLoading(true);
    setEntityError(null);
    fetchReportScope()
      .then((scope) => {
        if (!cancelled) {
          setEntities(scope.entities ?? []);
          setDocumentColumns(scope.documentColumns ?? []);
          setMetadataKeys(scope.metadataKeys ?? []);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setEntityError(normaliseError(error));
          setEntities([]);
          setDocumentColumns([]);
          setMetadataKeys([]);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setEntityLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    setTemplatesLoading(true);
    setTemplatesError(null);
    fetchReportTemplates()
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
  }, []);

  useEffect(() => {
    if (!selectedEntity) {
      setSelectedColumns([]);
      setFilters([]);
      setResult(null);
      setHasRun(false);
      setLastRequest(null);
      setRunError(null);
      return;
    }
    setSelectedColumns([]);
    setFilters([]);
    setResult(null);
    setHasRun(false);
    setLastRequest(null);
    setRunError(null);
    setPage(0);
    setMetadataLoading(true);
    setMetadataError(null);
    fetchReportScope(selectedEntity)
      .then((scope) => {
        setEntityColumns((prev) => ({ ...prev, [selectedEntity]: scope.baseColumns ?? [] }));
        setDocumentColumns(scope.documentColumns ?? []);
        setMetadataKeys(scope.metadataKeys ?? []);
      })
      .catch((error) => {
        setMetadataError(normaliseError(error));
      })
      .finally(() => {
        setMetadataLoading(false);
      });
  }, [selectedEntity]);

  useEffect(() => {
    if (!pendingTemplate) {
      return;
    }
    if (pendingTemplate.request.baseEntity !== selectedEntity) {
      return;
    }
    const baseCols = entityColumns[selectedEntity];
    if (!baseCols || baseCols.length === 0) {
      return;
    }

    const templateColumns = Array.isArray(pendingTemplate.request.columns)
      ? pendingTemplate.request.columns
      : [];
    const columnSet = new Set<string>(
      (templateColumns.length > 0 ? templateColumns : []).map((value) => normaliseTemplateKey(value)),
    );
    setSelectedColumns(Array.from(columnSet));

    const nextFilters: FilterRow[] = Array.isArray(pendingTemplate.request.filters)
      ? pendingTemplate.request.filters.map((filter) => {
          const operator = normalizeOperatorForBackend(filter?.op ?? 'EQ');
          const logicalType = (filter?.logicalType ?? filter?.dataType ?? 'STRING').toUpperCase() as LogicalType;
          const safeType: LogicalType = LOGICAL_TYPES.includes(logicalType) ? logicalType : 'STRING';
          const id = filterIdRef.current + 1;
          filterIdRef.current = id;
            return {
              id,
              key: normaliseTemplateKey(filter?.key ?? ''),
              op: operator,
              value: filter?.value ?? '',
              logicalType: safeType,
            };
          })
      : [];
    setFilters(nextFilters);
    setPendingTemplate(null);
    setHasRun(false);
    setResult(null);
    setRunError(null);
    setLastRequest(null);
    setPage(0);
  }, [pendingTemplate, selectedEntity, entityColumns]);

  const documentEntityName = useMemo(() => {
    const documentEntity = entities.find((entity) => !entity.joinsToDocument);
    return documentEntity?.name ?? 'DOCUMENT_PARENT';
  }, [entities]);

  const columnOptions: ColumnOption[] = useMemo(() => {
    if (!selectedEntity) {
      return [];
    }
    const options: ColumnOption[] = [];
    const seen = new Set<string>();

    const addOption = (value: string, label: string, group: ColumnOption['group']) => {
      if (!seen.has(value)) {
        seen.add(value);
        options.push({ value, label, group });
      }
    };

    const baseCols = entityColumns[selectedEntity] ?? [];
    baseCols.forEach((col) => addOption(col, `${selectedEntity} · ${col}`, 'Base Table'));

    documentColumns.forEach((col) =>
      addOption(`${documentEntityName}.${col}`, `${documentEntityName} · ${col}`, 'Document'),
    );

    metadataKeys.forEach((key) => addOption(`meta:${key}`, `Metadata · ${key}`, 'Metadata'));

    return options;
  }, [selectedEntity, entityColumns, documentColumns, documentEntityName, metadataKeys]);

  useEffect(() => {
    if (columnOptions.length === 0) {
      return;
    }
    const valid = new Set(columnOptions.map((option) => option.value));
    setSelectedColumns((prev) => {
      const next = prev.filter((column) => valid.has(column));
      if (next.length === 0 && columnOptions.length > 0) {
        next.push(columnOptions[0].value);
      }
      return Array.from(new Set(next));
    });
    setFilters((prev) =>
      prev.map((filter) => ({
        ...filter,
        key: filter.key && !valid.has(filter.key) ? '' : filter.key,
      })),
    );
  }, [columnOptions]);

  const handleColumnChange = (event: ChangeEvent<HTMLSelectElement>) => {
    const selected = Array.from(event.target.selectedOptions).map((option) => option.value);
    setSelectedColumns(Array.from(new Set(selected)));
  };

  const handleAddFilter = () => {
    const nextId = filterIdRef.current + 1;
    filterIdRef.current = nextId;
    setFilters((prev) => [...prev, { id: nextId, key: '', op: 'EQ', value: '', logicalType: 'STRING' }]);
  };

  const handleFilterChange = (
    id: number,
    update: Partial<Pick<FilterRow, 'key' | 'op' | 'value' | 'logicalType'>>,
  ) => {
    setFilters((prev) =>
      prev.map((filter) =>
        filter.id !== id
          ? filter
          : {
              ...filter,
              ...update,
              op: update.logicalType ? allowedOperators(update.logicalType)[0] : update.op ?? filter.op,
            },
      ),
    );
  };

  const handleRemoveFilter = (id: number) => {
    setFilters((prev) => prev.filter((filter) => filter.id !== id));
  };

  const handleSelectTemplateChange = (event: ChangeEvent<HTMLSelectElement>) => {
    const value = event.target.value;
    setTemplateSaveError(null);
    setTemplateSaveSuccess(null);
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

  const applyTemplate = useCallback(
    (template: ReportTemplate) => {
      setTemplateSaveError(null);
      setTemplateSaveSuccess(null);
      setSelectedTemplateId(template.id);
      setTemplateName(template.name);
      setPendingTemplate(template);
      setLoadedTemplate(template);
      setSelectedEntity(template.request.baseEntity);
    },
    [],
  );

  const handleApplySelectedTemplate = () => {
    if (selectedTemplateId == null) {
      setTemplateSaveError('Choose a template to load.');
      setTemplateSaveSuccess(null);
      return;
    }
    const template = templates.find((item) => item.id === selectedTemplateId);
    if (!template) {
      setTemplateSaveError('The selected template could not be found.');
      setTemplateSaveSuccess(null);
      return;
    }
    applyTemplate(template);
  };

  const buildTemplateRequest = useCallback((): DynamicReportRequest | null => {
    if (!selectedEntity) {
      return null;
    }
    const columns = Array.from(new Set(selectedColumns.map(normalizeKeyForBackend)));
    if (columns.length === 0) {
      return null;
    }

    const filtersPayload = filters
      .map((filter) => {
        if (!filter.key || !filter.op) {
          return null;
        }
        const normalizedKey = normalizeKeyForBackend(filter.key);
        const source = normalizedKey.startsWith('meta:')
          ? 'DOCUMENT_METADATA'
          : normalizedKey.startsWith(`${documentEntityName}.`) || normalizedKey.startsWith('DOCUMENT.')
          ? 'DOCUMENT'
          : 'THIRD_PARTY_ENTITY';
        const field = normalizedKey.includes('.') ? normalizedKey.split('.', 2)[1] : normalizedKey.replace('meta:', '');
        if (!filter.value) {
          return {
            key: normalizedKey,
            op: filter.op,
            value: '',
            mode: 'USER_INPUT' as const,
            source,
            field,
            logicalType: filter.logicalType,
            dataType: filter.logicalType,
            allowedOperators: allowedOperators(filter.logicalType),
          };
        }
        return {
          key: normalizedKey,
          op: filter.op,
          value: filter.value,
          mode: 'FIXED_VALUE' as const,
          source,
          field,
          logicalType: filter.logicalType,
          dataType: filter.logicalType,
          allowedOperators: allowedOperators(filter.logicalType),
        };
      })
      .filter((filter): filter is NonNullable<typeof filter> => Boolean(filter));

    return {
      baseEntity: selectedEntity,
      columns,
      filters: filtersPayload,
    };
  }, [selectedEntity, selectedColumns, filters, documentEntityName]);

  const buildRunRequest = useCallback((): DynamicReportRequest | null => {
    if (!selectedEntity) {
      return null;
    }
    const columns = Array.from(new Set(selectedColumns.map(normalizeKeyForBackend)));
    if (columns.length === 0) {
      return null;
    }

    const filtersPayload = filters
      .map((filter) => {
        if (!filter.key || !filter.op) {
          return null;
        }
        if (!filter.value) {
          return null;
        }
        return {
          key: normalizeKeyForBackend(filter.key),
          op: filter.op,
          value: filter.value,
          logicalType: filter.logicalType,
          dataType: filter.logicalType,
        };
      })
      .filter((filter): filter is NonNullable<typeof filter> => Boolean(filter));

    return {
      baseEntity: selectedEntity,
      columns,
      filters: filtersPayload,
    };
  }, [selectedEntity, selectedColumns, filters]);

  const handleSaveTemplate = useCallback(async () => {
    setTemplateSaveError(null);
    setTemplateSaveSuccess(null);
    const name = templateName.trim();
    if (!name) {
      setTemplateSaveError('Provide a template name before saving.');
      return;
    }
    const request = buildTemplateRequest();
    if (!request) {
      setTemplateSaveError('Define a report to save as a template.');
      return;
    }
    setSavingTemplate(true);
    try {
      const saved = await saveReportTemplate(name, request);
      setTemplates((prev) => {
        const filtered = prev.filter((template) => template.id !== saved.id);
        return [saved, ...filtered];
      });
      setTemplateSaveSuccess('Template saved successfully.');
      setSelectedTemplateId(saved.id);
      setLoadedTemplate(saved);
    } catch (error) {
      setTemplateSaveError(normaliseError(error));
    } finally {
      setSavingTemplate(false);
    }
  }, [buildTemplateRequest, templateName]);

  const handleUpdateTemplate = useCallback(async () => {
    if (!loadedTemplate) {
      return;
    }
    setTemplateSaveError(null);
    setTemplateSaveSuccess(null);
    const name = templateName.trim();
    if (!name) {
      setTemplateSaveError('Provide a template name before saving.');
      return;
    }
    const request = buildTemplateRequest();
    if (!request) {
      setTemplateSaveError('Define a report to save as a template.');
      return;
    }
    setSavingTemplate(true);
    try {
      const saved = await updateReportTemplate(loadedTemplate.id, name, request);
      setTemplates((prev) => prev.map((template) => (template.id === saved.id ? saved : template)));
      setTemplateSaveSuccess('Template updated successfully.');
      setSelectedTemplateId(saved.id);
      setLoadedTemplate(saved);
    } catch (error) {
      setTemplateSaveError(normaliseError(error));
    } finally {
      setSavingTemplate(false);
    }
  }, [buildTemplateRequest, loadedTemplate, templateName]);

  const handleSaveAsTemplate = useCallback(async () => {
    if (!loadedTemplate) {
      return;
    }
    setTemplateSaveError(null);
    setTemplateSaveSuccess(null);
    const name = templateName.trim();
    if (!name) {
      setTemplateSaveError('Provide a template name before saving.');
      return;
    }
    if (name === loadedTemplate.name.trim()) {
      setTemplateSaveError('Change the template name to save a new copy.');
      return;
    }
    const request = buildTemplateRequest();
    if (!request) {
      setTemplateSaveError('Define a report to save as a template.');
      return;
    }
    setSavingTemplate(true);
    try {
      const saved = await saveReportTemplate(name, request);
      setTemplates((prev) => {
        const filtered = prev.filter((template) => template.id !== saved.id);
        return [saved, ...filtered];
      });
      setTemplateSaveSuccess('Template saved successfully.');
      setSelectedTemplateId(saved.id);
      setLoadedTemplate(saved);
    } catch (error) {
      setTemplateSaveError(normaliseError(error));
    } finally {
      setSavingTemplate(false);
    }
  }, [buildTemplateRequest, loadedTemplate, templateName]);

  const executeReport = useCallback(
    async (request: DynamicReportRequest, targetPage: number, targetSize: number) => {
      setRunning(true);
      setRunError(null);
      try {
        const response = await runDynamicReport(request, targetPage, targetSize);
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

  const handleRunReport = async () => {
    const request = buildRunRequest();
    if (!request) {
      setRunError('Select a base entity and at least one column to run a report.');
      setHasRun(false);
      return;
    }
    setPage(0);
    setLastRequest(request);
    await executeReport(request, 0, pageSize);
  };

  const handlePageChange = async (nextPage: number) => {
    if (!lastRequest) {
      return;
    }
    setPage(nextPage);
    await executeReport(lastRequest, nextPage, pageSize);
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
    await executeReport(lastRequest, 0, nextSize);
  };

  const canRun = Boolean(selectedEntity && selectedColumns.length > 0);
  const canGoNext = Boolean(result && result.rows.length === pageSize);

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">Dynamic Reports</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">
          Build ad-hoc data extracts across DocFlow&apos;s entity stores. Select columns and filters, then run the report to preview
          results.
        </p>
      </header>

      <section className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Report Templates</h2>
        <p className="text-sm text-slate-600 dark:text-slate-300">
          Save frequently used report definitions and reload them later. Templates capture the entity, columns, and filters.
        </p>

        <div className="mt-4 grid gap-6 md:grid-cols-[1.5fr_2fr]">
          <div className="space-y-2">
            <label className="block text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
              Template Name
            </label>
            <div className="flex flex-col gap-2 sm:flex-row">
              <input
                type="text"
                value={templateName}
                onChange={(event) => setTemplateName(event.target.value)}
                placeholder="e.g. Approved loans by agent"
                className="w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
              />
              {loadedTemplate ? (
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    className="inline-flex items-center justify-center rounded bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-emerald-300 dark:bg-emerald-500 dark:hover:bg-emerald-400"
                    onClick={handleUpdateTemplate}
                    disabled={savingTemplate}
                  >
                    {savingTemplate ? 'Saving…' : 'Update Template'}
                  </button>
                  <button
                    type="button"
                    className="inline-flex items-center justify-center rounded border border-emerald-300 px-4 py-2 text-sm font-semibold text-emerald-700 shadow-sm transition hover:bg-emerald-50 disabled:cursor-not-allowed disabled:text-emerald-300 dark:border-emerald-400/60 dark:text-emerald-300 dark:hover:bg-emerald-500/10"
                    onClick={handleSaveAsTemplate}
                    disabled={
                      savingTemplate ||
                      !templateName.trim() ||
                      templateName.trim() === loadedTemplate.name.trim()
                    }
                  >
                    Save As
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  className="inline-flex items-center justify-center rounded bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-emerald-300 dark:bg-emerald-500 dark:hover:bg-emerald-400"
                  onClick={handleSaveTemplate}
                  disabled={savingTemplate}
                >
                  {savingTemplate ? 'Saving…' : 'Save Template'}
                </button>
              )}
            </div>
            {templateSaveError ? (
              <p className="text-xs text-red-600 dark:text-red-400">{templateSaveError}</p>
            ) : templateSaveSuccess ? (
              <p className="text-xs text-emerald-600 dark:text-emerald-400">{templateSaveSuccess}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <label className="block text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
              Saved Templates
            </label>
            {templatesLoading ? (
              <p className="text-sm text-slate-500 dark:text-slate-400">Loading templates…</p>
            ) : templatesError ? (
              <p className="text-sm text-red-600 dark:text-red-400">{templatesError}</p>
            ) : templates.length === 0 ? (
              <p className="text-sm text-slate-500 dark:text-slate-400">No templates saved yet.</p>
            ) : (
              <div className="space-y-2">
                <select
                  value={selectedTemplateId == null ? '' : String(selectedTemplateId)}
                  onChange={handleSelectTemplateChange}
                  className="w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                >
                  <option value="">Select a template…</option>
                  {templates.map((template) => (
                    <option key={template.id} value={String(template.id)}>
                      {template.name}
                    </option>
                  ))}
                </select>
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    className="inline-flex items-center rounded bg-slate-800 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-slate-900 disabled:cursor-not-allowed disabled:bg-slate-500 dark:bg-slate-600 dark:hover:bg-slate-500"
                    onClick={handleApplySelectedTemplate}
                    disabled={selectedTemplateId == null}
                  >
                    Load Template
                  </button>
                  {selectedTemplateId != null ? (
                    <button
                      type="button"
                      className="text-sm font-semibold text-blue-600 underline-offset-2 transition hover:underline dark:text-blue-400"
                      onClick={() => {
                        const template = templates.find((item) => item.id === selectedTemplateId);
                        if (template) {
                          applyTemplate(template);
                        }
                      }}
                    >
                      Reload definition
                    </button>
                  ) : null}
                </div>
              </div>
            )}
          </div>
        </div>
      </section>

      <section className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Report Definition</h2>
        <p className="text-sm text-slate-600 dark:text-slate-300">
          Choose a base entity and add filters to refine the dataset. Columns without a prefix refer to the base entity. Document and
          metadata fields are prefixed in the list below.
        </p>

        <div className="mt-6 grid gap-6 md:grid-cols-2">
          <div className="space-y-2">
            <label className="block text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
              Base Entity
            </label>
            <select
              className="w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
              value={selectedEntity}
              onChange={(event) => setSelectedEntity(event.target.value)}
              disabled={entityLoading}
            >
              <option value="">Select an entity…</option>
              {entities.map((entity) => (
                <option key={entity.name} value={entity.name}>
                  {entity.label ?? entity.name}
                </option>
              ))}
            </select>
            {entityLoading ? (
              <p className="text-xs text-slate-500 dark:text-slate-400">Loading entities…</p>
            ) : entityError ? (
              <p className="text-xs text-red-600 dark:text-red-400">{entityError}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <label className="block text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
              Columns
            </label>
            <select
              multiple
              size={Math.min(8, Math.max(4, columnOptions.length))}
              value={selectedColumns}
              onChange={handleColumnChange}
              className="w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
              disabled={!selectedEntity || metadataLoading}
            >
              {(['Base Table', 'Document', 'Metadata'] as const).map((group) => {
                const options = columnOptions.filter((option) => option.group === group);
                if (options.length === 0) {
                  return null;
                }
                return (
                  <optgroup key={group} label={group}>
                    {options.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </optgroup>
                );
              })}
            </select>
            {metadataLoading ? (
              <p className="text-xs text-slate-500 dark:text-slate-400">Loading columns…</p>
            ) : metadataError ? (
              <p className="text-xs text-red-600 dark:text-red-400">{metadataError}</p>
            ) : null}
          </div>
        </div>

        <div className="mt-6 space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-200">Filters</h3>
            <button
              type="button"
              className="inline-flex items-center rounded bg-blue-600 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-white shadow transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300 dark:bg-blue-500 dark:hover:bg-blue-400"
              onClick={handleAddFilter}
              disabled={!selectedEntity}
            >
              Add Filter
            </button>
          </div>
          {filters.length === 0 ? (
            <p className="text-sm text-slate-500 dark:text-slate-400">No filters applied.</p>
          ) : (
            <div className="space-y-2">
              {filters.map((filter) => (
                <div
                  key={filter.id}
                  className="grid gap-2 rounded border border-slate-200 p-3 text-sm transition-colors dark:border-slate-700 md:grid-cols-[1.5fr_1fr_1fr_1.5fr_auto]"
                >
                  <select
                    value={filter.key}
                    onChange={(event) => handleFilterChange(filter.id, { key: event.target.value })}
                    className="rounded border border-slate-300 px-2 py-1 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                  >
                    <option value="">Select column…</option>
                    {(['Base Table', 'Document', 'Metadata'] as const).map((group) => {
                      const options = columnOptions.filter((option) => option.group === group);
                      if (options.length === 0) {
                        return null;
                      }
                      return (
                        <optgroup key={group} label={group}>
                          {options.map((option) => (
                            <option key={option.value} value={option.value}>
                              {option.label}
                            </option>
                          ))}
                        </optgroup>
                      );
                    })}
                  </select>
                  <select
                    value={filter.logicalType}
                    onChange={(event) => handleFilterChange(filter.id, { logicalType: event.target.value as LogicalType })}
                    className="rounded border border-slate-300 px-2 py-1 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                  >
                    {LOGICAL_TYPES.map((type) => (
                      <option key={type} value={type}>
                        {type}
                      </option>
                    ))}
                  </select>
                  <select
                    value={filter.op}
                    onChange={(event) => handleFilterChange(filter.id, { op: event.target.value as Operator })}
                    className="rounded border border-slate-300 px-2 py-1 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                  >
                    {allowedOperators(filter.logicalType).map((operator) => (
                      <option key={operator} value={operator}>
                        {operator}
                      </option>
                    ))}
                  </select>
                  <input
                    type={filter.logicalType === 'NUMBER' ? 'number' : filter.logicalType === 'DATE' ? 'date' : 'text'}
                    value={filter.value}
                    onChange={(event) => handleFilterChange(filter.id, { value: event.target.value })}
                    inputMode={filter.logicalType === 'NUMBER' ? 'decimal' : undefined}
                    placeholder="Value"
                    className="rounded border border-slate-300 px-2 py-1 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                  />
                  <button
                    type="button"
                    className="self-start rounded border border-red-200 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-red-600 transition hover:bg-red-50 dark:border-red-400/40 dark:text-red-300 dark:hover:bg-red-500/10"
                    onClick={() => handleRemoveFilter(filter.id)}
                  >
                    Remove
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <div className="text-xs text-slate-500 dark:text-slate-400">
            Type-driven operators: STRING → EQ, NUMBER/DATE → EQ, LT, GT.
          </div>
          <button
            type="button"
            className="inline-flex items-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300 dark:bg-blue-500 dark:hover:bg-blue-400"
            onClick={handleRunReport}
            disabled={!canRun || running || Boolean(metadataError)}
          >
            {running ? 'Running…' : 'Run Report'}
          </button>
        </div>
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
