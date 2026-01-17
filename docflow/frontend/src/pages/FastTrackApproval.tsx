import { FormEvent, type ChangeEvent, type MouseEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import DocumentPreviewModal, { PreviewContent } from '../components/DocumentPreviewModal';
import DynamicForm from '../components/DynamicForm';
import StatusBadge from '../components/StatusBadge';
import api from '../lib/api';
import { parseUploadFieldConfig, UploadFieldDefinition } from '../lib/config';
import { parseReviewFilterConfig, ReviewFilterDefinition } from '../lib/reviewFilters';
import { convertDocxToHtml, convertXlsxToHtml } from '../lib/documentPreview';
import {
  DynamicFormValues,
  normaliseValuesForFields,
} from '../lib/dynamicFormValues';
import { buildFieldAccessMap } from '../lib/fieldAccess';
import { buildMetadataPayload } from '../lib/metadataPayload';
import { useUser } from '../lib/UserContext';
import { normalizeStatus } from '../lib/documentStatus';
import { mapAllowedActions, type WorkflowActionKey } from '../lib/workflowActions';
import type { DocumentResponse, DocumentSummary, PageResponse } from '../types/documents';

interface ConfigResponse {
  configJson: string | null;
}

type WorkflowAction = WorkflowActionKey;

type TimelineEntry = {
  eventLabel: string;
  eventCode: string;
  actorName: string;
  actorId: string;
  time: string;
  comment?: string | null;
  fromStatus?: string | null;
  toStatus?: string | null;
};

type RelatedEntityColumn = {
  key: string;
  label: string;
};

type RelatedEntityResponse = {
  entityName: string;
  label: string;
  columns: RelatedEntityColumn[];
  rows: Record<string, unknown>[];
};

type SortColumn = 'id' | 'documentNumber' | 'title' | 'status' | 'createdBy' | 'updatedBy' | 'updatedAt';

type DecisionChoice = 'A' | 'O' | 'R';

type PendingDecision = {
  decision: DecisionChoice;
  comment: string;
};

type FastTrackDecisionResult = {
  documentId: string;
  ok: boolean;
  newStatus?: string;
  errorCode?: string;
  message?: string;
};

type FastTrackDecisionSummary = {
  approved: number;
  onHold: number;
  rejected: number;
  failed: number;
};

type FastTrackDecisionSubmitResponse = {
  summary: FastTrackDecisionSummary;
  results: FastTrackDecisionResult[];
};

const PAGE_SIZE = 10;

export default function FastTrackApproval() {
  const { user } = useUser();
  const [configLoading, setConfigLoading] = useState(false);
  const [fields, setFields] = useState<UploadFieldDefinition[]>([]);
  const [documentIdFilter, setDocumentIdFilter] = useState('');
  const [reviewFilters, setReviewFilters] = useState<ReviewFilterDefinition[]>([]);
  const [reviewFilterValues, setReviewFilterValues] = useState<Record<string, string>>({});
  const [reviewFiltersLoading, setReviewFiltersLoading] = useState(false);
  const [reviewFilterError, setReviewFilterError] = useState<string | null>(null);
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
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [timelineOpen, setTimelineOpen] = useState(false);
  const [timelineLoading, setTimelineLoading] = useState(false);
  const [timelineError, setTimelineError] = useState<string | null>(null);
  const [timelineEntries, setTimelineEntries] = useState<TimelineEntry[]>([]);
  const [relatedEntityOpen, setRelatedEntityOpen] = useState(false);
  const [relatedEntityLoading, setRelatedEntityLoading] = useState(false);
  const [relatedEntityError, setRelatedEntityError] = useState<string | null>(null);
  const [relatedEntityLabel, setRelatedEntityLabel] = useState<string | null>(null);
  const [relatedEntityColumns, setRelatedEntityColumns] = useState<RelatedEntityColumn[]>([]);
  const [relatedEntityRows, setRelatedEntityRows] = useState<Record<string, unknown>[]>([]);
  const [pendingDecisions, setPendingDecisions] = useState<Record<number, PendingDecision>>({});
  const [decisionErrors, setDecisionErrors] = useState<Record<number, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitStatus, setSubmitStatus] = useState<string | null>(null);
  const [submittingDecisions, setSubmittingDecisions] = useState(false);
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

  const mergedFields = useMemo(() => {
    const existingMetadata = document?.metadata ?? {};
    const extraFields = Object.keys(existingMetadata)
      .filter((key) => !fields.some((field) => field.name === key))
      .map<UploadFieldDefinition>((key) => ({ name: key, label: key, type: 'text' }));
    return [...fields, ...extraFields];
  }, [fields, document]);

  const accessMap = useMemo(
    () =>
      buildFieldAccessMap(mergedFields, metadataValues, {
        activeRole: user?.role ?? null,
        documentStatus: normalizedStatus ?? document?.status ?? null,
      }),
    [mergedFields, metadataValues, user?.role, normalizedStatus, document?.status],
  );

  const visibleFields = useMemo(
    () => mergedFields.filter((field) => accessMap.get(field.name)?.isVisible ?? true),
    [mergedFields, accessMap],
  );

  const canEdit = useMemo(
    () => visibleFields.some((field) => accessMap.get(field.name)?.isEditable),
    [visibleFields, accessMap],
  );

  useEffect(() => {
    if (!document) {
      setMetadataValues({});
      return;
    }
    const initialValues = normaliseValuesForFields(mergedFields, document.metadata ?? {});
    setMetadataValues(initialValues);
  }, [document, mergedFields]);

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

  useEffect(() => {
    setTimelineOpen(false);
    setTimelineEntries([]);
    setTimelineError(null);
    setDetailsOpen(false);
    setRelatedEntityOpen(false);
    setRelatedEntityColumns([]);
    setRelatedEntityRows([]);
    setRelatedEntityError(null);
    setRelatedEntityLabel(null);
  }, [document?.id]);

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
        const filtersPayload = collectFilterPayload();
        const payload: Record<string, unknown> = {
          id: trimmedDocumentId,
          page: pageToLoad,
          size: PAGE_SIZE,
          sortBy: sortToUse,
          direction: directionToUse,
        };
        if (Object.keys(filtersPayload).length > 0) {
          payload.filters = filtersPayload;
        }
        const response = await api.post<PageResponse<DocumentSummary>>('/fast-track-approval/search', payload);
        const data = response.data;
        setDocumentsPage(data);
        setCurrentPage(data.number ?? pageToLoad);
        setSortBy(sortToUse);
        setSortDirection(directionToUse);
        if ((data.totalElements ?? 0) === 0) {
          setTableError('No documents found for the given criteria.');
        } else {
          setTableError(null);
        }
        setPendingDecisions((current) => {
          const allowedIds = new Set(data.content.map((item) => item.id));
          return Object.fromEntries(Object.entries(current).filter(([id]) => allowedIds.has(Number(id))));
        });
        setDecisionErrors((current) => {
          const allowedIds = new Set(data.content.map((item) => item.id));
          return Object.fromEntries(Object.entries(current).filter(([id]) => allowedIds.has(Number(id))));
        });
      } catch (error) {
        setTableError('Unable to load documents.');
      } finally {
        setDocumentsLoading(false);
      }
    },
    [collectFilterPayload, currentPage, sortBy, sortDirection, documentIdFilter],
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
      setDetailsOpen(false);
      setTimelineOpen(false);
      setRelatedEntityOpen(false);
      setTimelineEntries([]);
      setRelatedEntityColumns([]);
      setRelatedEntityRows([]);
      setRelatedEntityLabel(null);
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

  const updatedAtMap = useMemo(() => {
    const map = new Map<number, string | null>();
    documentsPage?.content.forEach((item) => map.set(item.id, item.updatedAt ?? null));
    return map;
  }, [documentsPage]);

  const decisionCounts = useMemo(() => {
    return Object.values(pendingDecisions).reduce(
      (acc, decision) => {
        if (decision.decision === 'A') {
          acc.approved += 1;
        }
        if (decision.decision === 'O') {
          acc.onHold += 1;
        }
        if (decision.decision === 'R') {
          acc.rejected += 1;
        }
        return acc;
      },
      { approved: 0, onHold: 0, rejected: 0 },
    );
  }, [pendingDecisions]);

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

  const handleDynamicFilterChange = useCallback((key: string, value: string) => {
    setReviewFilterValues((current) => ({
      ...current,
      [key]: value,
    }));
  }, []);

  const handleMetadataUpdate = async (values: DynamicFormValues) => {
    if (!document) {
      return;
    }
    if (!canEdit) {
      setErrorMessage('You do not have permission to update metadata for this document.');
      return;
    }

    try {
      setBusy(true);
      setErrorMessage(null);
      setStatusMessage(null);
      const metadata = buildMetadataPayload(mergedFields, values, document.metadata ?? {}, accessMap);
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

  const availableActions = useMemo(() => mapAllowedActions(document?.allowedActions), [document?.allowedActions]);
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
      const status = (error as { response?: { status?: number } })?.response?.status;
      if (status === 403) {
        setErrorMessage('You are not allowed to perform that workflow action.');
      } else {
        setErrorMessage('Workflow action failed.');
      }
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

  const fetchTimeline = useCallback(async () => {
    if (!document) {
      return;
    }
    setTimelineLoading(true);
    setTimelineError(null);
    try {
      const response = await api.get<TimelineEntry[]>(`/documents/${document.id}/timeline`);
      setTimelineEntries(response.data);
    } catch (error) {
      setTimelineEntries([]);
      setTimelineError('Unable to load timeline events.');
    } finally {
      setTimelineLoading(false);
    }
  }, [document]);

  useEffect(() => {
    if (timelineOpen) {
      void fetchTimeline();
    }
  }, [timelineOpen, fetchTimeline]);

  const fetchRelatedEntity = useCallback(async () => {
    if (!document) {
      return;
    }
    setRelatedEntityLoading(true);
    setRelatedEntityError(null);
    try {
      const response = await api.get<RelatedEntityResponse>(
        `/documents/${document.id}/related-entities/LOAN_DATA`,
      );
      setRelatedEntityLabel(response.data.label);
      setRelatedEntityColumns(response.data.columns ?? []);
      setRelatedEntityRows(response.data.rows ?? []);
    } catch (error) {
      setRelatedEntityLabel(null);
      setRelatedEntityColumns([]);
      setRelatedEntityRows([]);
      setRelatedEntityError('Unable to load related records.');
    } finally {
      setRelatedEntityLoading(false);
    }
  }, [document]);

  useEffect(() => {
    if (relatedEntityOpen) {
      void fetchRelatedEntity();
    }
  }, [relatedEntityOpen, fetchRelatedEntity]);

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

  const handleDecisionSelect = (event: MouseEvent<HTMLButtonElement>, summary: DocumentSummary, decision: DecisionChoice) => {
    event.stopPropagation();
    setPendingDecisions((current) => {
      const existing = current[summary.id];
      return {
        ...current,
        [summary.id]: {
          decision,
          comment: existing?.comment ?? '',
        },
      };
    });
    setDecisionErrors((current) => {
      const next = { ...current };
      delete next[summary.id];
      return next;
    });
  };

  const handleDecisionCommentChange = (event: ChangeEvent<HTMLTextAreaElement>, summary: DocumentSummary) => {
    event.stopPropagation();
    const value = event.target.value;
    setPendingDecisions((current) => {
      const existing = current[summary.id];
      if (!existing) {
        return current;
      }
      return {
        ...current,
        [summary.id]: {
          ...existing,
          comment: value,
        },
      };
    });
  };

  const handleSubmitDecisions = async () => {
    const decisionEntries = Object.entries(pendingDecisions);
    if (decisionEntries.length === 0) {
      return;
    }

    const nextErrors: Record<number, string> = {};
    decisionEntries.forEach(([id, decision]) => {
      if ((decision.decision === 'O' || decision.decision === 'R') && !decision.comment.trim()) {
        nextErrors[Number(id)] = 'Comment required for On Hold or Reject.';
      }
    });

    if (Object.keys(nextErrors).length > 0) {
      setDecisionErrors((current) => ({ ...current, ...nextErrors }));
      setSubmitError('Please add comments for all On Hold or Reject decisions.');
      return;
    }

    const confirmMessage = `Submit decisions?\nApproved: ${decisionCounts.approved}\nOn Hold: ${decisionCounts.onHold}\nRejected: ${decisionCounts.rejected}`;
    if (!window.confirm(confirmMessage)) {
      return;
    }

    setSubmittingDecisions(true);
    setSubmitError(null);
    setSubmitStatus(null);

    try {
      const payload = {
        decisions: decisionEntries.map(([id, decision]) => ({
          documentId: id,
          decision: decision.decision,
          comment: decision.comment.trim() ? decision.comment.trim() : null,
          expectedUpdatedAt: updatedAtMap.get(Number(id)) ?? null,
        })),
      };

      const response = await api.post<FastTrackDecisionSubmitResponse>('/fast-track-approval/submit-decisions', payload);
      const resultErrors: Record<number, string> = {};
      const succeededIds = new Set<number>();

      response.data.results.forEach((result) => {
        const numericId = Number(result.documentId);
        if (!result.ok) {
          resultErrors[numericId] = result.message ?? 'Decision failed.';
        } else {
          succeededIds.add(numericId);
        }
      });

      setDecisionErrors(resultErrors);
      setPendingDecisions((current) => {
        const next = { ...current };
        succeededIds.forEach((id) => {
          delete next[id];
        });
        return next;
      });
      setSubmitStatus(
        `Submitted decisions: ${response.data.summary.approved} approved, ${response.data.summary.onHold} on hold, ${response.data.summary.rejected} rejected.`,
      );
      await fetchDocuments({ resetSelection: true });
    } catch (error) {
      setSubmitError('Unable to submit decisions. Please try again.');
    } finally {
      setSubmittingDecisions(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <h1 className="text-xl font-semibold text-slate-800 dark:text-slate-100">Fast-track approval queue</h1>
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
          Filter the post-review queue and submit bulk approval, on-hold, or rejection decisions directly from the results list.
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
        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Results</h2>
            <p className="text-sm text-slate-600 dark:text-slate-300">
              {tableError
                ? tableError
                : documentsPage
                ? `Showing ${paginationSummary.start}–${paginationSummary.end} of ${paginationSummary.total} results`
                : 'Load documents to view the fast-track approval queue.'}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex flex-wrap items-center gap-2 text-xs font-semibold text-slate-600 dark:text-slate-300">
              <span className="rounded-full bg-emerald-100 px-2 py-1 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-200">
                A: {decisionCounts.approved}
              </span>
              <span className="rounded-full bg-orange-100 px-2 py-1 text-orange-700 dark:bg-orange-500/20 dark:text-orange-200">
                O: {decisionCounts.onHold}
              </span>
              <span className="rounded-full bg-rose-100 px-2 py-1 text-rose-700 dark:bg-rose-500/20 dark:text-rose-200">
                R: {decisionCounts.rejected}
              </span>
            </div>
            <button
              type="button"
              className="inline-flex items-center rounded bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-emerald-300 dark:bg-emerald-500 dark:hover:bg-emerald-400 dark:disabled:bg-emerald-400/60"
              disabled={submittingDecisions || Object.keys(pendingDecisions).length === 0}
              onClick={handleSubmitDecisions}
            >
              {submittingDecisions ? 'Submitting…' : 'Submit Decisions'}
            </button>
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
        {submitError ? <p className="mt-3 text-sm text-red-600 dark:text-red-400">{submitError}</p> : null}
        {submitStatus ? <p className="mt-3 text-sm text-green-600 dark:text-green-400">{submitStatus}</p> : null}
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
                <th scope="col" className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
                  Decision
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-slate-700">
              {tableError ? (
                <tr>
                  <td colSpan={7} className="px-4 py-6 text-center text-sm text-red-600 dark:text-red-400">
                    {tableError}
                  </td>
                </tr>
              ) : documentsLoading ? (
                <tr>
                  <td colSpan={7} className="px-4 py-6 text-center text-sm text-slate-500 dark:text-slate-400">
                    Loading documents…
                  </td>
                </tr>
              ) : documentsPage && documentsPage.content.length > 0 ? (
                documentsPage.content.map((summary) => {
                  const isSelected = selectedDocumentId === summary.id;
                  const pendingDecision = pendingDecisions[summary.id];
                  const decisionError = decisionErrors[summary.id];
                  const requiresComment = pendingDecision && (pendingDecision.decision === 'O' || pendingDecision.decision === 'R');
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
                      <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-200">
                        <div className="flex flex-col gap-2" onClick={(event) => event.stopPropagation()}>
                          <div className="flex flex-wrap gap-2">
                            <button
                              type="button"
                              className={`inline-flex h-7 w-7 items-center justify-center rounded border text-xs font-semibold transition ${
                                pendingDecision?.decision === 'A'
                                  ? 'border-emerald-600 bg-emerald-600 text-white'
                                  : 'border-emerald-200 text-emerald-700 hover:bg-emerald-50 dark:border-emerald-500/60 dark:text-emerald-200 dark:hover:bg-emerald-500/10'
                              }`}
                              onClick={(event) => handleDecisionSelect(event, summary, 'A')}
                            >
                              A
                            </button>
                            <button
                              type="button"
                              className={`inline-flex h-7 w-7 items-center justify-center rounded border text-xs font-semibold transition ${
                                pendingDecision?.decision === 'O'
                                  ? 'border-orange-500 bg-orange-500 text-white'
                                  : 'border-orange-200 text-orange-700 hover:bg-orange-50 dark:border-orange-400/60 dark:text-orange-200 dark:hover:bg-orange-500/10'
                              }`}
                              onClick={(event) => handleDecisionSelect(event, summary, 'O')}
                            >
                              O
                            </button>
                            <button
                              type="button"
                              className={`inline-flex h-7 w-7 items-center justify-center rounded border text-xs font-semibold transition ${
                                pendingDecision?.decision === 'R'
                                  ? 'border-rose-500 bg-rose-500 text-white'
                                  : 'border-rose-200 text-rose-700 hover:bg-rose-50 dark:border-rose-400/60 dark:text-rose-200 dark:hover:bg-rose-500/10'
                              }`}
                              onClick={(event) => handleDecisionSelect(event, summary, 'R')}
                            >
                              R
                            </button>
                          </div>
                          <textarea
                            className="w-full rounded border border-slate-200 px-2 py-1 text-xs text-slate-700 transition focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                            rows={2}
                            placeholder={
                              pendingDecision
                                ? requiresComment
                                  ? 'Comment required for On Hold / Reject'
                                  : 'Comment (optional)'
                                : 'Select a decision to add comment'
                            }
                            value={pendingDecision?.comment ?? ''}
                            onChange={(event) => handleDecisionCommentChange(event, summary)}
                            disabled={!pendingDecision}
                          />
                          {decisionError ? (
                            <span className="text-xs text-red-600 dark:text-red-400">{decisionError}</span>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  );
                })
              ) : (
                <tr>
                  <td colSpan={7} className="px-4 py-6 text-center text-sm text-slate-500 dark:text-slate-400">
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
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  className="inline-flex h-7 w-7 items-center justify-center rounded border border-slate-300 text-sm font-semibold text-slate-600 transition hover:bg-slate-100 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
                  onClick={() => setDetailsOpen((current) => !current)}
                  aria-expanded={detailsOpen}
                  aria-label="Toggle document details"
                >
                  {detailsOpen ? '−' : '+'}
                </button>
                <div className="flex items-center gap-2">
                  <h3 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Document Details</h3>
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-200">
                    {document ? 1 : 0}
                  </span>
                </div>
              </div>
            </div>
            {detailsOpen ? (
              <div className="mt-4 space-y-6">
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
                  {configLoading && visibleFields.length === 0 ? (
                    <p className="mt-4 text-sm text-slate-500 dark:text-slate-300">Loading field configuration…</p>
                  ) : visibleFields.length === 0 ? (
                    <p className="mt-4 text-sm text-slate-500 dark:text-slate-300">No metadata fields configured for this role.</p>
                  ) : (
                    <DynamicForm
                      fields={visibleFields}
                      initialValues={metadataValues}
                      onChange={handleMetadataChange}
                      onSubmit={canEdit ? handleMetadataUpdate : undefined}
                      submitLabel={canEdit ? 'Save Metadata' : null}
                      disabled={busy || !canEdit}
                      accessMap={accessMap}
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
          </div>

          <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  className="inline-flex h-7 w-7 items-center justify-center rounded border border-slate-300 text-sm font-semibold text-slate-600 transition hover:bg-slate-100 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
                  onClick={() => setTimelineOpen((current) => !current)}
                  aria-expanded={timelineOpen}
                  aria-label="Toggle document timeline"
                >
                  {timelineOpen ? '−' : '+'}
                </button>
                <div className="flex items-center gap-2">
                  <h3 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Document Timeline</h3>
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-200">
                    {timelineEntries.length}
                  </span>
                </div>
              </div>
              <Link
                to={`/audit?docId=${encodeURIComponent(String(document.id))}`}
                className="text-sm font-semibold text-blue-600 transition hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300"
              >
                View Detailed Audit
              </Link>
            </div>
            {timelineOpen ? (
              <div className="mt-4 space-y-3">
                {timelineLoading ? (
                  <p className="text-sm text-slate-500 dark:text-slate-300">Loading timeline…</p>
                ) : timelineError ? (
                  <p className="text-sm text-red-600 dark:text-red-400">{timelineError}</p>
                ) : timelineEntries.length === 0 ? (
                  <p className="text-sm text-slate-500 dark:text-slate-300">No timeline events yet.</p>
                ) : (
                  <div className="overflow-hidden rounded border border-slate-200 dark:border-slate-700">
                    <table className="min-w-full divide-y divide-slate-200 text-sm dark:divide-slate-700">
                      <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500 dark:bg-slate-800 dark:text-slate-300">
                        <tr>
                          <th className="px-3 py-2">Time</th>
                          <th className="px-3 py-2">Event</th>
                          <th className="px-3 py-2">User</th>
                          <th className="px-3 py-2">Notes</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-200 dark:divide-slate-700">
                        {timelineEntries.map((entry, index) => (
                          <tr key={`${entry.eventCode}-${index}`} className="bg-white dark:bg-slate-900">
                            <td className="px-3 py-2 text-slate-600 dark:text-slate-300">
                              {formatTimestamp(entry.time)}
                            </td>
                            <td className="px-3 py-2 text-slate-700 dark:text-slate-200">{entry.eventLabel}</td>
                            <td className="px-3 py-2 text-slate-700 dark:text-slate-200">
                              {entry.actorName || entry.actorId}
                            </td>
                            <td className="px-3 py-2 text-slate-600 dark:text-slate-300">
                              {formatTimelineNotes(entry)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            ) : null}
          </div>

          <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  className="inline-flex h-7 w-7 items-center justify-center rounded border border-slate-300 text-sm font-semibold text-slate-600 transition hover:bg-slate-100 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
                  onClick={() => setRelatedEntityOpen((current) => !current)}
                  aria-expanded={relatedEntityOpen}
                  aria-label="Toggle related entity details"
                >
                  {relatedEntityOpen ? '−' : '+'}
                </button>
                <div className="flex items-center gap-2">
                  <h3 className="text-lg font-semibold text-slate-800 dark:text-slate-100">
                    {relatedEntityLabel ?? 'Related Records'}
                  </h3>
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-200">
                    {relatedEntityRows.length}
                  </span>
                </div>
              </div>
            </div>
            {relatedEntityOpen ? (
              <div className="mt-4 space-y-3">
                {relatedEntityLoading ? (
                  <p className="text-sm text-slate-500 dark:text-slate-300">Loading related records…</p>
                ) : relatedEntityError ? (
                  <p className="text-sm text-red-600 dark:text-red-400">{relatedEntityError}</p>
                ) : relatedEntityRows.length === 0 ? (
                  <p className="text-sm text-slate-500 dark:text-slate-300">No related records found.</p>
                ) : (
                  <div className="overflow-hidden rounded border border-slate-200 dark:border-slate-700">
                    <table className="min-w-full divide-y divide-slate-200 text-sm dark:divide-slate-700">
                      <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500 dark:bg-slate-800 dark:text-slate-300">
                        <tr>
                          {relatedEntityColumns.map((column) => (
                            <th key={column.key} className="px-3 py-2">
                              {column.label}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-200 dark:divide-slate-700">
                        {relatedEntityRows.map((row, index) => (
                          <tr key={`related-${index}`} className="bg-white dark:bg-slate-900">
                            {relatedEntityColumns.map((column) => (
                              <td key={column.key} className="px-3 py-2 text-slate-600 dark:text-slate-300">
                                {formatRelatedValue(row[column.key])}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            ) : null}
          </div>
        </div>
      ) : (
        <div className="rounded border border-dashed border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">
          Select a document to view details.
        </div>
      )}
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

function formatTimestamp(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function formatTimelineNotes(entry: TimelineEntry): string {
  if (entry.comment) {
    return entry.comment;
  }
  const fromStatus = entry.fromStatus ?? '—';
  const toStatus = entry.toStatus ?? '—';
  if (fromStatus === '—' && toStatus === '—') {
    return '—';
  }
  return `${fromStatus} → ${toStatus}`;
}

function formatRelatedValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '—';
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }
  return String(value);
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

function AuthRequired() {
  return (
    <div className="rounded border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-300">
      Please sign in via the Login page to access fast-track approvals.
    </div>
  );
}
