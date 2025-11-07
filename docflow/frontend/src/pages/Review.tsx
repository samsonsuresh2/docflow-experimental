import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import DocumentPreviewModal, { PreviewContent } from '../components/DocumentPreviewModal';
import DynamicForm from '../components/DynamicForm';
import StatusBadge from '../components/StatusBadge';
import api from '../lib/api';
import { fieldsForRole, parseUploadFieldConfig, UploadFieldDefinition } from '../lib/config';
import { parseReviewFilterConfig, ReviewFilterDefinition } from '../lib/reviewFilters';
import { convertDocxToHtml, convertXlsxToHtml } from '../lib/documentPreview';
import {
  DynamicFormValues,
  isDynamicFormValueEmpty,
  normaliseValuesForFields,
} from '../lib/dynamicFormValues';
import { useUser } from '../lib/UserContext';
import { DocumentStatus, normalizeStatus } from '../lib/documentStatus';
import type { UserRole } from '../lib/user';
import type { DocumentResponse, DocumentSummary, PageResponse } from '../types/documents';

interface ConfigResponse {
  configJson: string | null;
}

type WorkflowAction = 'submit' | 'startReview' | 'approve' | 'reject' | 'rework' | 'close';

type StatusFilter =
  | 'ALL'
  | 'DRAFT'
  | 'OPEN'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'CLOSED';
type SortColumn = 'id' | 'documentNumber' | 'title' | 'status' | 'createdBy' | 'updatedBy' | 'updatedAt';

const PAGE_SIZE = 10;

const STATUS_FILTER_OPTIONS: Array<{ value: StatusFilter; label: string }> = [
  { value: 'ALL', label: 'All' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'OPEN', label: 'Open' },
  { value: 'UNDER_REVIEW', label: 'Under Review' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'CLOSED', label: 'Closed' },
];

export default function Review() {
  const { user } = useUser();
  const [configLoading, setConfigLoading] = useState(false);
  const [fields, setFields] = useState<UploadFieldDefinition[]>([]);
  const [documentIdFilter, setDocumentIdFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [reviewFilters, setReviewFilters] = useState<ReviewFilterDefinition[]>([]);
  const [reviewFilterValues, setReviewFilterValues] = useState<Record<string, string>>({});
  const [reviewFiltersLoading, setReviewFiltersLoading] = useState(false);
  const [reviewFilterError, setReviewFilterError] = useState<string | null>(null);
  // const [metadataKeyFilter, setMetadataKeyFilter] = useState('');
  // const [metadataValueFilter, setMetadataValueFilter] = useState('');
  const [documentsPage, setDocumentsPage] = useState<PageResponse<DocumentSummary> | null>(null);
  const [documentsLoading, setDocumentsLoading] = useState(false);
  const [selectedDocumentId, setSelectedDocumentId] = useState<number | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [sortBy, setSortBy] = useState<SortColumn>('id');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');
  const [tableError, setTableError] = useState<string | null>(null);
  const [document, setDocument] = useState<DocumentResponse | null>(null);
  const [metadataValues, setMetadataValues] = useState<DynamicFormValues>({});
  const [loadingDocument, setLoadingDocument] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [actionComment, setActionComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [previewContent, setPreviewContent] = useState<PreviewContent | null>(null);
  const [previewDownloadUrl, setPreviewDownloadUrl] = useState<string | null>(null);
  const [previewFileName, setPreviewFileName] = useState<string | null>(null);
  const documentDetailsRef = useRef<HTMLDivElement | null>(null);

  const normalizedStatus = normalizeStatus(document?.status ?? null);

  useEffect(() => {
    let isMounted = true;
    const loadConfig = async () => {
      try {
        setConfigLoading(true);
        const response = await api.get<ConfigResponse>('/config/upload-fields');
        if (!isMounted) {
          return;
        }
        setFields(parseUploadFieldConfig(response.data.configJson));
      } catch (error) {
        if (isMounted) {
          setErrorMessage('Failed to load upload field configuration.');
        }
      } finally {
        if (isMounted) {
          setConfigLoading(false);
        }
      }
    };

    loadConfig();

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    let isMounted = true;
    const loadFilters = async () => {
      try {
        setReviewFiltersLoading(true);
        setReviewFilterError(null);
        const response = await api.get<ConfigResponse>('/admin/config/review-filters');
        if (!isMounted) {
          return;
        }
        const definitions = parseReviewFilterConfig(response.data.configJson);
        setReviewFilters(definitions);
        setReviewFilterValues((current) => {
          const nextValues: Record<string, string> = {};
          definitions.forEach((definition) => {
            const existing = current[definition.key];
            nextValues[definition.key] = typeof existing === 'string' ? existing : '';
          });
          return nextValues;
        });
      } catch (error) {
        if (isMounted) {
          setReviewFilters([]);
          setReviewFilterValues({});
          setReviewFilterError('Failed to load review filter configuration.');
        }
      } finally {
        if (isMounted) {
          setReviewFiltersLoading(false);
        }
      }
    };

    loadFilters();

    return () => {
      isMounted = false;
    };
  }, []);

  const availableFields = useMemo(() => {
    const baseFields = fieldsForRole(fields, user?.role);
    const existingMetadata = document?.metadata ?? {};
    const extraFields = Object.keys(existingMetadata)
      .filter((key) => !baseFields.some((field) => field.name === key))
      .map<UploadFieldDefinition>((key) => ({ name: key, label: key, type: 'text' }));

    const editable = canEditMetadata(user?.role ?? null, normalizedStatus);

    return [...baseFields, ...extraFields].map((field) => ({
      ...field,
      readOnly: Boolean(field.readOnly) || !editable,
    }));
  }, [fields, user?.role, document, normalizedStatus]);

  useEffect(() => {
    if (!document) {
      setMetadataValues({});
      return;
    }
    const initialValues = normaliseValuesForFields(availableFields, document.metadata ?? {});
    setMetadataValues(initialValues);
  }, [document, availableFields]);

  useEffect(() => {
    return () => {
      if (previewDownloadUrl) {
        URL.revokeObjectURL(previewDownloadUrl);
      }
    };
  }, [previewDownloadUrl]);

  useEffect(() => {
    if (document && documentDetailsRef.current) {
      documentDetailsRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }, [document]);

  if (!user) {
    return <AuthRequired />;
  }

  const collectFilterPayload = useCallback(() => {
    const payload: Record<string, string> = {};
    reviewFilters.forEach((definition) => {
      const rawValue = reviewFilterValues[definition.key];
      if (typeof rawValue !== 'string') {
        return;
      }
      const trimmed = rawValue.trim();
      if (trimmed) {
        if (definition.type === 'date' && !/^[<>~=]/.test(trimmed)) {
          payload[definition.key] = `>${trimmed}`;
        } else {
          payload[definition.key] = trimmed;
        }
      }
    });
    return payload;
  }, [reviewFilters, reviewFilterValues]);

  const fetchDocuments = useCallback(
    async ({
      page,
      sort,
      direction,
      resetSelection,
    }: {
      page?: number;
      sort?: SortColumn;
      direction?: 'asc' | 'desc';
      resetSelection?: boolean;
    } = {}) => {
      const pageToLoad = page ?? currentPage;
      const sortToUse = sort ?? sortBy;
      const directionToUse = direction ?? sortDirection;

      if (resetSelection) {
        setSelectedDocumentId(null);
        setDocument(null);
      }

      setDocumentsLoading(true);
      setTableError(null);
      try {
        const trimmedDocumentId = documentIdFilter.trim();
        // const trimmedMetadataKey = metadataKeyFilter.trim();
        // const trimmedMetadataValue = metadataValueFilter.trim();
        const filtersPayload = collectFilterPayload();
        const params: Record<string, unknown> = {
            status: statusFilter === 'ALL' ? '' : statusFilter,
            id: trimmedDocumentId,
            page: pageToLoad,
            size: PAGE_SIZE,
            sortBy: sortToUse,
            direction: directionToUse,
            // metadataKey: trimmedMetadataKey,
            // metadataValue: trimmedMetadataValue,
        };
        if (Object.keys(filtersPayload).length > 0) {
          params.filters = JSON.stringify(filtersPayload);
        }
        const response = await api.get<PageResponse<DocumentSummary>>('/documents/search', {
          params,
        });
        const data = response.data;
        setDocumentsPage(data);
        setCurrentPage(data.number ?? pageToLoad);
        setSortBy(sortToUse);
        setSortDirection(directionToUse);
      } catch (error) {
        setTableError('Unable to load documents.');
      } finally {
        setDocumentsLoading(false);
      }
    },
    [collectFilterPayload, currentPage, sortBy, sortDirection, documentIdFilter, statusFilter],
  );

  const handleFilterSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      fetchDocuments({ page: 0, resetSelection: true });
    },
    [fetchDocuments],
  );

  const loadDocument = useCallback(
    async (documentId: number) => {
      setLoadingDocument(true);
      setErrorMessage(null);
      setStatusMessage(null);
      try {
        const response = await api.get<DocumentResponse>(`/documents/${documentId}`);
        setDocument(response.data);
        setSelectedDocumentId(response.data.id);
      } catch (error) {
        setDocument(null);
        setSelectedDocumentId(null);
        setErrorMessage('Document not found.');
      } finally {
        setLoadingDocument(false);
      }
    },
    [],
  );

  const handleRowClick = useCallback(
    (summary: DocumentSummary) => {
      setSelectedDocumentId(summary.id);
      setStatusMessage(null);
      setErrorMessage(null);
      void loadDocument(summary.id);
    },
    [loadDocument],
  );

  const handleSort = useCallback(
    (column: SortColumn) => {
      const nextDirection = sortBy === column && sortDirection === 'asc' ? 'desc' : 'asc';
      fetchDocuments({ page: 0, sort: column, direction: nextDirection });
    },
    [fetchDocuments, sortBy, sortDirection],
  );

  const handlePreviousPage = useCallback(() => {
    if (!documentsPage || documentsLoading || currentPage === 0) {
      return;
    }
    fetchDocuments({ page: currentPage - 1 });
  }, [documentsPage, documentsLoading, currentPage, fetchDocuments]);

  const handleNextPage = useCallback(() => {
    if (!documentsPage || documentsLoading) {
      return;
    }
    const nextPage = currentPage + 1;
    if (nextPage >= documentsPage.totalPages) {
      return;
    }
    fetchDocuments({ page: nextPage });
  }, [documentsPage, documentsLoading, currentPage, fetchDocuments]);

  const paginationSummary = useMemo(() => {
    if (!documentsPage || documentsPage.totalElements === 0) {
      return { start: 0, end: 0, total: documentsPage?.totalElements ?? 0 };
    }
    const start = documentsPage.number * documentsPage.size + 1;
    const end = start + documentsPage.content.length - 1;
    return { start, end, total: documentsPage.totalElements };
  }, [documentsPage]);

  const canGoPrevious = documentsPage !== null && currentPage > 0;
  const canGoNext = documentsPage !== null && currentPage + 1 < documentsPage.totalPages;

  const renderSortIndicator = useCallback(
    (column: SortColumn) => {
      if (sortBy !== column) {
        return '↕';
      }
      return sortDirection === 'asc' ? '↑' : '↓';
    },
    [sortBy, sortDirection],
  );

  const handleMetadataChange = useCallback((values: DynamicFormValues) => {
    setMetadataValues(values);
  }, []);

  const handleMetadataUpdate = async (values: DynamicFormValues) => {
    if (!document) {
      return;
    }
    const editable = canEditMetadata(user.role, normalizedStatus);
    if (!editable) {
      setErrorMessage('You do not have permission to update metadata for this document.');
      return;
    }

    try {
      setBusy(true);
      setErrorMessage(null);
      setStatusMessage(null);
      const metadata = buildMetadataPayload(availableFields, values);
      await api.put(`/documents/${document.id}/metadata`, { metadata });
      setStatusMessage('Metadata updated successfully.');
      await loadDocument(document.id);
      await fetchDocuments();
    } catch (error) {
      setErrorMessage('Unable to update metadata.');
    } finally {
      setBusy(false);
    }
  };

  const availableActions = determineActions(user.role, normalizedStatus);
  const canEdit = canEditMetadata(user.role, normalizedStatus);
  const canPreview = Boolean(document?.filePath);

  const handleWorkflowAction = async (action: WorkflowAction) => {
    if (!document) {
      return;
    }
    try {
      setBusy(true);
      setErrorMessage(null);
      setStatusMessage(null);
      const commentPayload = actionComment.trim() ? { comment: actionComment.trim() } : undefined;
      switch (action) {
        case 'submit':
          await api.put(`/documents/${document.id}/submit`);
          break;
        case 'startReview':
          await api.put(`/documents/${document.id}/under-review`, commentPayload);
          break;
        case 'approve':
          await api.put(`/documents/${document.id}/approve`, commentPayload);
          break;
        case 'reject':
          await api.put(`/documents/${document.id}/reject`, commentPayload);
          break;
        case 'rework':
          await api.put(`/documents/${document.id}/rework`, commentPayload);
          break;
        case 'close':
          await api.put(`/documents/${document.id}/close`, commentPayload);
          break;
        default:
          break;
      }
      setStatusMessage('Workflow updated successfully.');
      setActionComment('');
      await loadDocument(document.id);
      await fetchDocuments();
    } catch (error) {
      setErrorMessage('Workflow action failed.');
    } finally {
      setBusy(false);
    }
  };

  const handlePreview = async () => {
    if (!document || !document.filePath) {
      return;
    }

    if (previewDownloadUrl) {
      URL.revokeObjectURL(previewDownloadUrl);
      setPreviewDownloadUrl(null);
    }

    const fileName =
      extractFileName(document.filePath) ?? `document-${document.documentNumber}`;
    setPreviewFileName(fileName);
    setPreviewOpen(true);
    setPreviewLoading(true);
    setPreviewError(null);
    setPreviewContent(null);

    let objectUrl: string | null = null;
    try {
      const response = await api.get<Blob>(`/documents/download/${document.id}`, {
        responseType: 'blob',
      });
      const blob = response.data as Blob;
      objectUrl = URL.createObjectURL(blob);
      setPreviewDownloadUrl(objectUrl);

      const extension = determineFileExtension(fileName, response.headers['content-type'] as string | undefined);

      let content: PreviewContent;
      switch (extension) {
        case 'pdf':
          content = { kind: 'pdf', url: objectUrl };
          break;
        case 'txt': {
          const text = await blob.text();
          content = { kind: 'text', text };
          break;
        }
        case 'docx': {
          const html = await convertDocxToHtml(await blob.arrayBuffer());
          content = { kind: 'docx', html: html.html };
          break;
        }
        case 'xlsx': {
          const html = await convertXlsxToHtml(await blob.arrayBuffer());
          content = { kind: 'xlsx', html: html.html };
          break;
        }
        default:
          content = { kind: 'unsupported', message: 'Preview not available — use Download instead.' };
          break;
      }

      setPreviewContent(content);
    } catch (error) {
      if (objectUrl) {
        const message =
          error instanceof Error && error.message.includes('DecompressionStream')
            ? 'Preview not available in this browser. Please download the file instead.'
            : 'Preview not available — use Download instead.';
        setPreviewContent({ kind: 'unsupported', message });
        setPreviewError(null);
      } else {
        setPreviewError('Unable to load document preview.');
      }
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleClosePreview = () => {
    setPreviewOpen(false);
    setPreviewLoading(false);
    setPreviewError(null);
    setPreviewContent(null);
    setPreviewFileName(null);
    if (previewDownloadUrl) {
      URL.revokeObjectURL(previewDownloadUrl);
      setPreviewDownloadUrl(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <h1 className="text-xl font-semibold text-slate-800 dark:text-slate-100">Document review</h1>
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
          Filter the document queue and load results to review metadata, capture maker updates, or drive workflow transitions
          based on your role.
        </p>
        <form className="mt-4 grid gap-4 md:grid-cols-4" onSubmit={handleFilterSubmit}>
          <div className="flex flex-col gap-1">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">Document ID</span>
            <input
              type="text"
              placeholder="Document ID (e.g. DOC-2025-000123)"
              className="rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
              value={documentIdFilter}
              onChange={(event) => setDocumentIdFilter(event.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">Status</span>
            <select
              className="rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
            >
              {STATUS_FILTER_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
          {reviewFilters.map((filter) => (
            <div key={filter.key} className="flex flex-col gap-1">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
                {filter.label}
              </span>
              {filter.type === 'dropdown' ? (
                <select
                  className="rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                  value={reviewFilterValues[filter.key] ?? ''}
                  onChange={(event) => handleDynamicFilterChange(filter.key, event.target.value)}
                  disabled={documentsLoading || reviewFiltersLoading}
                >
                  <option value="">All</option>
                  {(filter.options ?? []).map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  type={filter.type === 'date' ? 'date' : 'text'}
                  className="rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                  value={reviewFilterValues[filter.key] ?? ''}
                  onChange={(event) => handleDynamicFilterChange(filter.key, event.target.value)}
                  disabled={documentsLoading || reviewFiltersLoading}
                />
              )}
            </div>
          ))}
          <div className="flex items-end gap-2 md:col-span-4">
            <button
              type="submit"
              className="inline-flex items-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300 dark:bg-blue-500 dark:hover:bg-blue-400 dark:disabled:bg-blue-400/60"
              disabled={documentsLoading}
            >
              {documentsLoading ? 'Loading…' : 'Load Documents'}
            </button>
            {reviewFiltersLoading ? (
              <span className="text-xs text-slate-500 dark:text-slate-300">Loading filters…</span>
            ) : null}
            {reviewFilterError ? (
              <span className="text-xs text-red-600 dark:text-red-400">{reviewFilterError}</span>
            ) : null}
          </div>
        </form>
      </div>

      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Results</h2>
            <p className="text-sm text-slate-600 dark:text-slate-300">
              {tableError
                ? tableError
                : documentsPage
                ? `Showing ${paginationSummary.start}–${paginationSummary.end} of ${paginationSummary.total} results`
                : 'Load documents to view the review queue.'}
            </p>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              className="inline-flex items-center rounded border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-100 dark:hover:bg-slate-800"
              onClick={handlePreviousPage}
              disabled={!canGoPrevious || documentsLoading}
            >
              Previous
            </button>
            <button
              type="button"
              className="inline-flex items-center rounded border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-100 dark:hover:bg-slate-800"
              onClick={handleNextPage}
              disabled={!canGoNext || documentsLoading}
            >
              Next
            </button>
          </div>
        </div>
        <div className="mt-4 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 dark:divide-slate-700">
            <thead className="bg-slate-50 dark:bg-slate-800/60">
              <tr>
                <th scope="col" className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
                  <button
                    type="button"
                    className="flex items-center gap-1 text-left text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-slate-100"
                    onClick={() => handleSort('documentNumber')}
                  >
                    Document ID
                    <span className="text-xs">{renderSortIndicator('documentNumber')}</span>
                  </button>
                </th>
                <th scope="col" className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
                  <button
                    type="button"
                    className="flex items-center gap-1 text-left text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-slate-100"
                    onClick={() => handleSort('title')}
                  >
                    Name
                    <span className="text-xs">{renderSortIndicator('title')}</span>
                  </button>
                </th>
                <th scope="col" className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
                  <button
                    type="button"
                    className="flex items-center gap-1 text-left text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-slate-100"
                    onClick={() => handleSort('status')}
                  >
                    Status
                    <span className="text-xs">{renderSortIndicator('status')}</span>
                  </button>
                </th>
                <th scope="col" className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
                  <button
                    type="button"
                    className="flex items-center gap-1 text-left text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-slate-100"
                    onClick={() => handleSort('createdBy')}
                  >
                    Created By
                    <span className="text-xs">{renderSortIndicator('createdBy')}</span>
                  </button>
                </th>
                <th scope="col" className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
                  <button
                    type="button"
                    className="flex items-center gap-1 text-left text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-slate-100"
                    onClick={() => handleSort('updatedBy')}
                  >
                    Last Updated By
                    <span className="text-xs">{renderSortIndicator('updatedBy')}</span>
                  </button>
                </th>
                <th scope="col" className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
                  <button
                    type="button"
                    className="flex items-center gap-1 text-left text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-slate-100"
                    onClick={() => handleSort('updatedAt')}
                  >
                    Last Updated At
                    <span className="text-xs">{renderSortIndicator('updatedAt')}</span>
                  </button>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-slate-700">
              {tableError ? (
                <tr>
                  <td colSpan={6} className="px-4 py-6 text-center text-sm text-red-600 dark:text-red-400">
                    {tableError}
                  </td>
                </tr>
              ) : documentsLoading ? (
                <tr>
                  <td colSpan={6} className="px-4 py-6 text-center text-sm text-slate-500 dark:text-slate-400">
                    Loading documents…
                  </td>
                </tr>
              ) : documentsPage && documentsPage.content.length > 0 ? (
                documentsPage.content.map((summary) => {
                  const isSelected = selectedDocumentId === summary.id;
                  return (
                    <tr
                      key={summary.id}
                      onClick={() => handleRowClick(summary)}
                      className={`cursor-pointer transition hover:bg-slate-50 dark:hover:bg-slate-800 ${
                        isSelected
                          ? 'bg-blue-50 hover:bg-blue-100 dark:bg-blue-500/10 dark:hover:bg-blue-500/20'
                          : 'bg-white dark:bg-slate-900'
                      }`}
                    >
                      <td className="whitespace-nowrap px-4 py-3 text-sm font-medium text-slate-700 dark:text-slate-200">
                        {summary.documentNumber}
                      </td>
                      <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-200">{summary.title}</td>
                      <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-200">
                        <StatusBadge status={summary.status} />
                      </td>
                      <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-200">{summary.createdBy}</td>
                      <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-200">{summary.updatedBy ?? '—'}</td>
                      <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-200">
                        {formatTimestamp(summary.updatedAt ?? summary.createdAt ?? null)}
                      </td>
                    </tr>
                  );
                })
              ) : (
                <tr>
                  <td colSpan={6} className="px-4 py-6 text-center text-sm text-slate-500 dark:text-slate-400">
                    {documentsPage ? 'No documents match your filters.' : 'Use the filters above and load documents to begin.'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {errorMessage ? <p className="text-sm text-red-600 dark:text-red-400">{errorMessage}</p> : null}
      {statusMessage ? <p className="text-sm text-green-600 dark:text-green-400">{statusMessage}</p> : null}

      {document ? (
        <div ref={documentDetailsRef} className="space-y-6">
          <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
            <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
              <div>
                <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">{document.title}</h2>
                <p className="text-sm text-slate-600 dark:text-slate-300">Document #{document.documentNumber}</p>
              </div>
              <StatusBadge status={document.status} />
            </div>
            <dl className="mt-4 grid gap-4 md:grid-cols-2">
              <div>
                <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Created By</dt>
                <dd className="text-sm text-slate-700 dark:text-slate-200">{document.createdBy}</dd>
              </div>
              <div>
                <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Last Updated By</dt>
                <dd className="text-sm text-slate-700 dark:text-slate-200">{document.updatedBy ?? '—'}</dd>
              </div>
            </dl>
          </div>

          <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Metadata</h3>
              {canEdit ? (
                <span className="text-xs font-semibold uppercase tracking-wide text-emerald-600 dark:text-emerald-400">Editable</span>
              ) : (
                <span className="text-xs font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500">Read only</span>
              )}
            </div>
            {configLoading && availableFields.length === 0 ? (
              <p className="mt-4 text-sm text-slate-500 dark:text-slate-300">Loading field configuration…</p>
            ) : availableFields.length === 0 ? (
              <p className="mt-4 text-sm text-slate-500 dark:text-slate-300">No metadata fields configured for this role.</p>
            ) : (
              <DynamicForm
                fields={availableFields}
                initialValues={metadataValues}
                onChange={handleMetadataChange}
                onSubmit={canEdit ? handleMetadataUpdate : undefined}
                submitLabel={canEdit ? 'Save Metadata' : null}
                disabled={busy || !canEdit}
              />
            )}
          </div>
          {canPreview || availableActions.length > 0 ? (
            <div className="space-y-4 rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
              <div>
                <h3 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Workflow Actions</h3>
                <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">
                  Preview the uploaded file or trigger the next state transition permitted for your role.
                </p>
              </div>
              {availableActions.length > 0 ? (
                <label className="block text-sm">
                  <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">Comment (optional)</span>
                  <textarea
                    className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                    rows={3}
                    value={actionComment}
                    onChange={(event) => setActionComment(event.target.value)}
                  />
                </label>
              ) : null}
              <div className="flex flex-wrap gap-2">
                {canPreview ? (
                  <button
                    type="button"
                    className="inline-flex items-center rounded border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-100 dark:border-slate-600 dark:text-slate-100 dark:hover:bg-slate-800"
                    onClick={handlePreview}
                  >
                    Preview
                  </button>
                ) : null}
                {availableActions.map((action) => (
                  <button
                    key={action.key}
                    type="button"
                    className="inline-flex items-center rounded bg-slate-900 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:bg-slate-400 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-slate-200 dark:disabled:bg-slate-700 dark:disabled:text-slate-300"
                    onClick={() => handleWorkflowAction(action.key)}
                    disabled={busy}
                  >
                    {busy ? 'Processing…' : action.label}
                  </button>
                ))}
              </div>
            </div>
          ) : null}
        </div>
      ) : null}
      <DocumentPreviewModal
        isOpen={previewOpen}
        onClose={handleClosePreview}
        loading={previewLoading}
        error={previewError}
        content={previewContent}
        downloadUrl={previewDownloadUrl}
        fileName={previewFileName}
      />
    </div>
  );
}

function canEditMetadata(role: UserRole | null, status: DocumentStatus | null) {
  if (!role || !status) {
    return false;
  }
  if (role !== 'MAKER') {
    return false;
  }
  return status === 'DRAFT' || status === 'REWORK';
}

function determineActions(role: UserRole, status: DocumentStatus | null): { key: WorkflowAction; label: string }[] {
  if (!status) {
    return [];
  }
  if (role === 'ADMIN') {
    return buildFullActionList(status);
  }

  switch (role) {
    case 'MAKER':
      if (status === 'DRAFT') {
        return [{ key: 'submit', label: 'Submit for Review' }];
      }
      if (status === 'REWORK') {
        return [{ key: 'submit', label: 'Resubmit for Review' }];
      }
      return [];
    case 'REVIEWER':
    case 'CHECKER':
      return determineReviewerCheckerActions(status);
    default:
      return [];
  }
}

function determineReviewerCheckerActions(status: DocumentStatus): { key: WorkflowAction; label: string }[] {
  switch (status) {
    case 'SUBMITTED':
      return [{ key: 'startReview', label: 'Start Review' }];
    case 'UNDER_REVIEW':
      return [
        { key: 'approve', label: 'Approve' },
        { key: 'rework', label: 'Rework' },
        { key: 'reject', label: 'Reject' },
      ];
    case 'APPROVED':
    case 'REJECTED':
      return [{ key: 'close', label: 'Close Document' }];
    default:
      return [];
  }
}

function buildFullActionList(status: DocumentStatus): { key: WorkflowAction; label: string }[] {
  switch (status) {
    case 'DRAFT':
      return [{ key: 'submit', label: 'Submit for Review' }];
    case 'REWORK':
      return [{ key: 'submit', label: 'Resubmit for Review' }];
    case 'SUBMITTED':
      return [{ key: 'startReview', label: 'Start Review' }];
    case 'UNDER_REVIEW':
      return [
        { key: 'approve', label: 'Approve' },
        { key: 'rework', label: 'Rework' },
        { key: 'reject', label: 'Reject' },
      ];
    case 'APPROVED':
    case 'REJECTED':
      return [{ key: 'close', label: 'Close Document' }];
    default:
      return [];
  }
}

function formatTimestamp(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function extractFileName(filePath: string): string | null {
  const trimmed = filePath.trim();
  if (!trimmed) {
    return null;
  }
  const withoutQuery = trimmed.split('?')[0] ?? trimmed;
  const segments = withoutQuery.split(/[/\\]/);
  const lastSegment = segments[segments.length - 1];
  return lastSegment && lastSegment.length > 0 ? lastSegment : null;
}

function determineFileExtension(fileName: string, contentType?: string): string | null {
  const sanitized = fileName.trim();
  const withoutQuery = sanitized.split('?')[0] ?? sanitized;
  const lastDot = withoutQuery.lastIndexOf('.');
  if (lastDot > 0 && lastDot < withoutQuery.length - 1) {
    return withoutQuery.slice(lastDot + 1).toLowerCase();
  }

  if (contentType) {
    const normalizedContentType = contentType.toLowerCase();
    if (normalizedContentType.includes('application/pdf')) {
      return 'pdf';
    }
    if (normalizedContentType.includes('text/plain')) {
      return 'txt';
    }
    if (normalizedContentType.includes('wordprocessingml')) {
      return 'docx';
    }
    if (normalizedContentType.includes('spreadsheetml')) {
      return 'xlsx';
    }
  }

  return null;
}

function buildMetadataPayload(
  fields: UploadFieldDefinition[],
  values: DynamicFormValues,
): Record<string, unknown> {
  const metadata: Record<string, unknown> = {};
  fields.forEach((field) => {
    const rawValue = values[field.name];
    if (rawValue === undefined) {
      return;
    }
    if (typeof rawValue !== 'boolean' && isDynamicFormValueEmpty(rawValue)) {
      return;
    }
    switch (field.type) {
      case 'number': {
        if (typeof rawValue === 'string') {
          const parsed = Number(rawValue);
          metadata[field.name] = Number.isNaN(parsed) ? rawValue : parsed;
        } else {
          metadata[field.name] = rawValue;
        }
        break;
      }
      case 'checkbox': {
        metadata[field.name] = Boolean(rawValue);
        break;
      }
      case 'multiselect':
      case 'checkbox-group': {
        if (Array.isArray(rawValue)) {
          if (rawValue.length > 0) {
            metadata[field.name] = rawValue;
          }
        } else if (typeof rawValue === 'string' && rawValue) {
          metadata[field.name] = [rawValue];
        }
        break;
      }
      default:
        metadata[field.name] = rawValue;
    }
  });
  return metadata;
}

function AuthRequired() {
  return (
    <div className="rounded border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-300">
      Please sign in via the Login page to review documents.
    </div>
  );
}
  const handleDynamicFilterChange = useCallback((key: string, value: string) => {
    setReviewFilterValues((current) => ({
      ...current,
      [key]: value,
    }));
  }, []);

