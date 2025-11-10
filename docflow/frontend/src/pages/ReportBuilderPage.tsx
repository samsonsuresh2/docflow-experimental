import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react';
import ReportResultsGrid from '../components/ReportResultsGrid';
import {
  fetchReportEntities,
  fetchReportMetadata,
  fetchReportTemplates,
  runDynamicReport,
  saveReportTemplate,
} from '../lib/reports';
import type {
  DynamicReportRequest,
  ReportMetadata,
  ReportRelationship,
  ReportRunResponse,
  ReportTemplate,
} from '../types/reports';

const OPERATORS = ['=', '<', '>', '<=', '>=', 'like', 'between'] as const;
type Operator = (typeof OPERATORS)[number];

type FilterRow = {
  id: number;
  key: string;
  op: Operator;
  value: string;
  valueTo?: string;
};

type ColumnOption = {
  value: string;
  label: string;
};

type RelationshipOption = ReportRelationship & { key: string };

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

export default function ReportBuilderPage() {
  const [entityOptions, setEntityOptions] = useState<string[]>([]);
  const [entityLoading, setEntityLoading] = useState<boolean>(true);
  const [entityError, setEntityError] = useState<string | null>(null);

  const [selectedEntity, setSelectedEntity] = useState<string>('');
  const [metadataCache, setMetadataCache] = useState<Record<string, ReportMetadata>>({});
  const [metadataLoading, setMetadataLoading] = useState<boolean>(false);
  const [metadataError, setMetadataError] = useState<string | null>(null);
  const metadataCacheRef = useRef<Record<string, ReportMetadata>>({});

  useEffect(() => {
    metadataCacheRef.current = metadataCache;
  }, [metadataCache]);

  const [selectedRelationships, setSelectedRelationships] = useState<string[]>([]);
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

  const filterIdRef = useRef<number>(0);

  useEffect(() => {
    let cancelled = false;
    setEntityLoading(true);
    setEntityError(null);
    fetchReportEntities()
      .then((entities) => {
        if (!cancelled) {
          setEntityOptions(entities);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setEntityError(normaliseError(error));
          setEntityOptions([]);
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

  const loadMetadata = useCallback(async (entity: string) => {
    const key = entity.trim().toLowerCase();
    if (!key) {
      return;
    }
    if (metadataCacheRef.current[key]) {
      return;
    }
    setMetadataLoading(true);
    setMetadataError(null);
    try {
      const metadata = await fetchReportMetadata(key);
      setMetadataCache((prev) => ({ ...prev, [metadata.entity]: metadata }));
    } catch (error) {
      setMetadataError(normaliseError(error));
      throw error;
    } finally {
      setMetadataLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!selectedEntity) {
      setSelectedColumns([]);
      setSelectedRelationships([]);
      setFilters([]);
      setResult(null);
      setHasRun(false);
      setLastRequest(null);
      setRunError(null);
      return;
    }
    setSelectedColumns(['entity_id']);
    setSelectedRelationships([]);
    setFilters([]);
    setResult(null);
    setHasRun(false);
    setLastRequest(null);
    setRunError(null);
    setPage(0);
    loadMetadata(selectedEntity).catch(() => {
      // error handled in loadMetadata
    });
  }, [selectedEntity, loadMetadata]);

  useEffect(() => {
    selectedRelationships.forEach((key) => {
      const [target] = key.split('|');
      if (target && !metadataCache[target]) {
        loadMetadata(target).catch(() => {
          // handled in loader
        });
      }
    });
  }, [selectedRelationships, metadataCache, loadMetadata]);

  useEffect(() => {
    if (!pendingTemplate) {
      return;
    }
    if (pendingTemplate.request.baseEntity !== selectedEntity) {
      return;
    }
    if (!metadataCache[pendingTemplate.request.baseEntity]) {
      return;
    }

    const templateColumns = Array.isArray(pendingTemplate.request.columns)
      ? pendingTemplate.request.columns
      : [];
    const columnSet = new Set<string>(templateColumns.length > 0 ? templateColumns : ['entity_id']);
    columnSet.add('entity_id');
    setSelectedColumns(Array.from(columnSet));

    const validRelationshipKeys = new Set(availableRelationships.map((relationship) => relationship.key));
    const joinKeys = new Set<string>();
    if (Array.isArray(pendingTemplate.request.joins)) {
      pendingTemplate.request.joins.forEach((join) => {
        if (!join || !join.rightEntity || !join.on) {
          return;
        }
        const [left] = join.on.split('=');
        const via = left?.trim();
        if (!via) {
          return;
        }
        const key = `${join.rightEntity}|${via}`;
        if (validRelationshipKeys.size === 0 || validRelationshipKeys.has(key)) {
          joinKeys.add(key);
        }
      });
    }
    setSelectedRelationships(Array.from(joinKeys));

    const nextFilters: FilterRow[] = Array.isArray(pendingTemplate.request.filters)
      ? pendingTemplate.request.filters.map((filter) => {
          const rawOp = (filter?.op ?? '=') as Operator;
          const operator: Operator = OPERATORS.includes(rawOp) ? rawOp : '=';
          let value = filter?.value ?? '';
          let valueTo: string | undefined;
          if (operator === 'between') {
            const parts = value.split(',', 2);
            value = parts[0]?.trim() ?? '';
            valueTo = parts[1]?.trim() ?? '';
          }
          const id = filterIdRef.current + 1;
          filterIdRef.current = id;
          return {
            id,
            key: filter?.key ?? '',
            op: operator,
            value,
            valueTo,
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
  }, [pendingTemplate, selectedEntity, metadataCache, availableRelationships]);

  const availableRelationships: RelationshipOption[] = useMemo(() => {
    if (!selectedEntity) {
      return [];
    }
    const metadata = metadataCache[selectedEntity];
    if (!metadata) {
      return [];
    }
    return metadata.relationships.map((relationship) => ({
      ...relationship,
      key: `${relationship.to}|${relationship.via}`,
    }));
  }, [metadataCache, selectedEntity]);

  const columnOptions: ColumnOption[] = useMemo(() => {
    if (!selectedEntity) {
      return [];
    }
    const options: ColumnOption[] = [];
    const values = new Set<string>();

    const addOption = (value: string, label: string) => {
      if (!values.has(value)) {
        values.add(value);
        options.push({ value, label });
      }
    };

    addOption('entity_id', `${selectedEntity} · entity_id`);

    const baseMetadata = metadataCache[selectedEntity];
    if (baseMetadata) {
      baseMetadata.availableKeys.forEach((key) => {
        addOption(key, `${selectedEntity} · ${key}`);
      });
    }

    selectedRelationships.forEach((relationshipKey) => {
      const [target] = relationshipKey.split('|');
      if (!target) {
        return;
      }
      addOption(`${target}.entity_id`, `${target} · entity_id`);
      const joinMetadata = metadataCache[target];
      joinMetadata?.availableKeys.forEach((key) => {
        addOption(`${target}.${key}`, `${target} · ${key}`);
      });
    });

    return options;
  }, [metadataCache, selectedEntity, selectedRelationships]);

  useEffect(() => {
    if (columnOptions.length === 0) {
      return;
    }
    const valid = new Set(columnOptions.map((option) => option.value));
    setSelectedColumns((prev) => {
      const next = prev.filter((column) => valid.has(column));
      if (!next.includes('entity_id')) {
        next.unshift('entity_id');
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
    const unique = new Set(selected);
    unique.add('entity_id');
    setSelectedColumns(Array.from(unique));
  };

  const handleRelationshipToggle = (relationshipKey: string) => {
    setSelectedRelationships((prev) => {
      if (prev.includes(relationshipKey)) {
        return prev.filter((key) => key !== relationshipKey);
      }
      return [...prev, relationshipKey];
    });
  };

  const handleAddFilter = () => {
    const nextId = filterIdRef.current + 1;
    filterIdRef.current = nextId;
    setFilters((prev) => [...prev, { id: nextId, key: '', op: '=', value: '' }]);
  };

  const handleFilterChange = (
    id: number,
    update: Partial<Pick<FilterRow, 'key' | 'op' | 'value' | 'valueTo'>>,
  ) => {
    setFilters((prev) =>
      prev.map((filter) =>
        filter.id === id
          ? {
              ...filter,
              ...update,
              valueTo: update.op && update.op !== 'between' ? '' : update.valueTo ?? filter.valueTo,
            }
          : filter,
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
      setSelectedEntity(template.request.baseEntity);
      loadMetadata(template.request.baseEntity).catch(() => {
        // handled by loader
      });
    },
    [loadMetadata],
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

  const buildRequest = useCallback((): DynamicReportRequest | null => {
    if (!selectedEntity) {
      return null;
    }
    const metadata = metadataCache[selectedEntity];
    if (!metadata) {
      return null;
    }

    const columns = Array.from(new Set(selectedColumns));
    if (!columns.includes('entity_id')) {
      columns.unshift('entity_id');
    }

    const relationshipMap = new Map(availableRelationships.map((relationship) => [relationship.key, relationship]));
    const joins = selectedRelationships
      .map((key) => relationshipMap.get(key))
      .filter((relationship): relationship is RelationshipOption => Boolean(relationship))
      .map((relationship) => ({
        rightEntity: relationship.to,
        on: `${relationship.via}=${relationship.via}`,
      }));

    const filtersPayload = filters
      .map((filter) => {
        if (!filter.key || !filter.op) {
          return null;
        }
        if (filter.op === 'between') {
          if (!filter.value || !filter.valueTo) {
            return null;
          }
          return {
            key: filter.key,
            op: filter.op,
            value: `${filter.value},${filter.valueTo}`,
          };
        }
        if (!filter.value) {
          return null;
        }
        return {
          key: filter.key,
          op: filter.op,
          value: filter.value,
        };
      })
      .filter((filter): filter is NonNullable<typeof filter> => Boolean(filter));

    return {
      baseEntity: metadata.entity,
      columns,
      filters: filtersPayload,
      joins,
    };
  }, [
    selectedEntity,
    metadataCache,
    selectedColumns,
    availableRelationships,
    selectedRelationships,
    filters,
  ]);

  const handleSaveTemplate = useCallback(async () => {
    setTemplateSaveError(null);
    setTemplateSaveSuccess(null);
    const name = templateName.trim();
    if (!name) {
      setTemplateSaveError('Provide a template name before saving.');
      return;
    }
    const request = buildRequest();
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
    } catch (error) {
      setTemplateSaveError(normaliseError(error));
    } finally {
      setSavingTemplate(false);
    }
  }, [buildRequest, templateName]);

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
    const request = buildRequest();
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
          Build ad-hoc data extracts across DocFlow&apos;s entity stores. Select columns, relationships, and filters, then run the
          report to preview results.
        </p>
      </header>

      <section className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Report Templates</h2>
        <p className="text-sm text-slate-600 dark:text-slate-300">
          Save frequently used report definitions and reload them later. Templates capture the entity, columns, joins, and filters.
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
              <button
                type="button"
                className="inline-flex items-center justify-center rounded bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-emerald-300 dark:bg-emerald-500 dark:hover:bg-emerald-400"
                onClick={handleSaveTemplate}
                disabled={savingTemplate}
              >
                {savingTemplate ? 'Saving…' : 'Save Template'}
              </button>
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
          Choose a base entity, include optional joins, and add filters to refine the dataset. Columns without a prefix refer to the
          base entity. Joined entity columns are prefixed with the entity name.
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
              {entityOptions.map((entity) => (
                <option key={entity} value={entity}>
                  {entity}
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
              Columns (entity_id is always included)
            </label>
            <select
              multiple
              size={Math.min(8, Math.max(4, columnOptions.length))}
              value={selectedColumns}
              onChange={handleColumnChange}
              className="w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
              disabled={!selectedEntity || metadataLoading}
            >
              {columnOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            {metadataLoading ? (
              <p className="text-xs text-slate-500 dark:text-slate-400">Inspecting metadata…</p>
            ) : metadataError ? (
              <p className="text-xs text-red-600 dark:text-red-400">{metadataError}</p>
            ) : null}
          </div>
        </div>

        <div className="mt-6 grid gap-6 md:grid-cols-2">
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-200">Relationships</h3>
            {availableRelationships.length === 0 ? (
              <p className="text-sm text-slate-500 dark:text-slate-400">No configured relationships for this entity.</p>
            ) : (
              <div className="space-y-2">
                {availableRelationships.map((relationship) => (
                  <label
                    key={relationship.key}
                    className="flex items-start gap-2 rounded border border-transparent p-2 text-sm transition hover:border-blue-200 hover:bg-blue-50 dark:hover:border-blue-500/40 dark:hover:bg-blue-500/10"
                  >
                    <input
                      type="checkbox"
                      className="mt-1 h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                      checked={selectedRelationships.includes(relationship.key)}
                      onChange={() => handleRelationshipToggle(relationship.key)}
                    />
                    <span className="leading-tight text-slate-700 dark:text-slate-200">
                      Join <span className="font-semibold">{relationship.to}</span> via <code className="rounded bg-slate-100 px-1 py-0.5 text-xs dark:bg-slate-800">{relationship.via}</code>
                    </span>
                  </label>
                ))}
              </div>
            )}
          </div>

          <div className="space-y-3">
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
                    className="grid gap-2 rounded border border-slate-200 p-3 text-sm transition-colors dark:border-slate-700 md:grid-cols-[1.5fr_1fr_1.5fr_1.5fr_auto]"
                  >
                    <select
                      value={filter.key}
                      onChange={(event) => handleFilterChange(filter.id, { key: event.target.value })}
                      className="rounded border border-slate-300 px-2 py-1 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                    >
                      <option value="">Select column…</option>
                      {columnOptions.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                    <select
                      value={filter.op}
                      onChange={(event) =>
                        handleFilterChange(filter.id, {
                          op: event.target.value as Operator,
                          valueTo: event.target.value === 'between' ? filter.valueTo ?? '' : '',
                        })
                      }
                      className="rounded border border-slate-300 px-2 py-1 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                    >
                      {OPERATORS.map((operator) => (
                        <option key={operator} value={operator}>
                          {operator.toUpperCase()}
                        </option>
                      ))}
                    </select>
                    <input
                      type="text"
                      value={filter.value}
                      onChange={(event) => handleFilterChange(filter.id, { value: event.target.value })}
                      placeholder={filter.op === 'between' ? 'From value' : 'Value'}
                      className="rounded border border-slate-300 px-2 py-1 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                    />
                    {filter.op === 'between' ? (
                      <input
                        type="text"
                        value={filter.valueTo ?? ''}
                        onChange={(event) => handleFilterChange(filter.id, { valueTo: event.target.value })}
                        placeholder="To value"
                        className="rounded border border-slate-300 px-2 py-1 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                      />
                    ) : (
                      <div className="hidden md:block" />
                    )}
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
        </div>

        <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <div className="text-xs text-slate-500 dark:text-slate-400">
            Operators support LIKE wildcards (%) and BETWEEN accepts a start and end value.
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
