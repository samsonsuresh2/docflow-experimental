import { FormEvent, useEffect, useMemo, useState } from 'react';
import DynamicForm from '../components/DynamicForm';
import StatusBadge from '../components/StatusBadge';
import api from '../lib/api';
import { fieldsForRole, parseUploadFieldConfig, UploadFieldDefinition } from '../lib/config';
import { useUser } from '../lib/UserContext';
import type { UserRole } from '../lib/user';

interface ConfigResponse {
  configJson: string | null;
}

interface DocumentResponse {
  id: number;
  documentNumber: string;
  title: string;
  status: string;
  createdBy: string;
  updatedBy: string | null;
  metadata: Record<string, unknown> | null;
}

type WorkflowAction = 'submit' | 'startReview' | 'approve' | 'rework' | 'close';

export default function Review() {
  const { user } = useUser();
  const [configLoading, setConfigLoading] = useState(false);
  const [fields, setFields] = useState<UploadFieldDefinition[]>([]);
  const [documentIdInput, setDocumentIdInput] = useState('');
  const [document, setDocument] = useState<DocumentResponse | null>(null);
  const [metadataValues, setMetadataValues] = useState<Record<string, string>>({});
  const [loadingDocument, setLoadingDocument] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [actionComment, setActionComment] = useState('');
  const [busy, setBusy] = useState(false);

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

  const availableFields = useMemo(() => {
    const baseFields = fieldsForRole(fields, user?.role);
    const existingMetadata = document?.metadata ?? {};
    const extraFields = Object.keys(existingMetadata)
      .filter((key) => !baseFields.some((field) => field.name === key))
      .map<UploadFieldDefinition>((key) => ({ name: key, label: key, type: 'text' }));

    const editable = canEditMetadata(user?.role ?? null, document?.status ?? null);

    return [...baseFields, ...extraFields].map((field) => ({
      ...field,
      readOnly: Boolean(field.readOnly) || !editable,
    }));
  }, [fields, user?.role, document]);

  useEffect(() => {
    if (!document) {
      setMetadataValues({});
      return;
    }
    const initialValues: Record<string, string> = {};
    Object.entries(document.metadata ?? {}).forEach(([key, value]) => {
      if (value === null || value === undefined) {
        initialValues[key] = '';
      } else if (value instanceof Date) {
        initialValues[key] = value.toISOString();
      } else {
        initialValues[key] = String(value);
      }
    });
    setMetadataValues(initialValues);
  }, [document]);

  if (!user) {
    return <AuthRequired />;
  }

  const handleDocumentSearch = async (event: FormEvent) => {
    event.preventDefault();
    if (!documentIdInput.trim()) {
      return;
    }
    const id = Number(documentIdInput.trim());
    if (Number.isNaN(id)) {
      setErrorMessage('Document ID must be numeric.');
      return;
    }

    await loadDocument(id);
  };

  const loadDocument = async (id: number) => {
    setLoadingDocument(true);
    setErrorMessage(null);
    setStatusMessage(null);
    try {
      const response = await api.get<DocumentResponse>(`/documents/${id}`);
      setDocument(response.data);
    } catch (error) {
      setDocument(null);
      setErrorMessage('Document not found.');
    } finally {
      setLoadingDocument(false);
    }
  };

  const handleMetadataUpdate = async (values: Record<string, string>) => {
    if (!document) {
      return;
    }
    const editable = canEditMetadata(user.role, document.status);
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
    } catch (error) {
      setErrorMessage('Unable to update metadata.');
    } finally {
      setBusy(false);
    }
  };

  const availableActions = determineActions(user.role, document?.status ?? null);
  const canEdit = document ? canEditMetadata(user.role, document.status) : false;

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
    } catch (error) {
      setErrorMessage('Workflow action failed.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <h1 className="text-xl font-semibold text-slate-800 dark:text-slate-100">Document review</h1>
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
          Search for a document ID to review metadata, capture maker updates, or drive approval transitions based on your
          current role.
        </p>
        <form className="mt-4 flex flex-wrap gap-2" onSubmit={handleDocumentSearch}>
          <input
            type="text"
            placeholder="Document ID"
            className="flex-1 min-w-[180px] rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
            value={documentIdInput}
            onChange={(event) => setDocumentIdInput(event.target.value)}
          />
          <button
            type="submit"
            className="inline-flex items-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-400"
            disabled={loadingDocument}
          >
            {loadingDocument ? 'Loading…' : 'Load Document'}
          </button>
        </form>
      </div>

      {errorMessage ? <p className="text-sm text-red-600 dark:text-red-400">{errorMessage}</p> : null}
      {statusMessage ? <p className="text-sm text-green-600 dark:text-green-400">{statusMessage}</p> : null}

      {document ? (
        <div className="space-y-6">
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
                onChange={(values) => setMetadataValues(values)}
                onSubmit={canEdit ? handleMetadataUpdate : undefined}
                submitLabel={canEdit ? 'Save Metadata' : null}
                disabled={busy || !canEdit}
              />
            )}
          </div>
          {availableActions.length > 0 ? (
            <div className="space-y-4 rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
              <div>
                <h3 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Workflow Actions</h3>
                <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">Trigger the next state transition permitted for your role.</p>
              </div>
              <label className="block text-sm">
                <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">Comment (optional)</span>
                <textarea
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                  rows={3}
                  value={actionComment}
                  onChange={(event) => setActionComment(event.target.value)}
                />
              </label>
              <div className="flex flex-wrap gap-2">
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
  );
}

function canEditMetadata(role: UserRole | null, status: string | null) {
  if (!role || !status) {
    return false;
  }
  if (role === 'ADMIN') {
    return true;
  }
  switch (role) {
    case 'MAKER':
      return status === 'Draft' || status === 'Rework';
    case 'REVIEWER':
      return status === 'Under Review';
    default:
      return false;
  }
}

function determineActions(role: UserRole, status: string | null): { key: WorkflowAction; label: string }[] {
  if (!status) {
    return [];
  }
  if (role === 'ADMIN') {
    return buildFullActionList(status);
  }

  switch (role) {
    case 'MAKER':
      return status === 'Draft' || status === 'Rework'
        ? [{ key: 'submit', label: 'Submit for Review' }]
        : [];
    case 'REVIEWER':
      if (status === 'Submitted') {
        return [{ key: 'startReview', label: 'Start Review' }];
      }
      if (status === 'Under Review') {
        return [
          { key: 'approve', label: 'Approve' },
          { key: 'rework', label: 'Request Rework' },
        ];
      }
      return [];
    case 'CHECKER':
      return status === 'Approved' ? [{ key: 'close', label: 'Close Document' }] : [];
    default:
      return [];
  }
}

function buildFullActionList(status: string): { key: WorkflowAction; label: string }[] {
  const actions: { key: WorkflowAction; label: string }[] = [];
  if (status === 'Draft' || status === 'Rework') {
    actions.push({ key: 'submit', label: 'Submit for Review' });
  }
  if (status === 'Submitted') {
    actions.push({ key: 'startReview', label: 'Start Review' });
  }
  if (status === 'Under Review') {
    actions.push({ key: 'approve', label: 'Approve' }, { key: 'rework', label: 'Request Rework' });
  }
  if (status === 'Approved') {
    actions.push({ key: 'close', label: 'Close Document' });
  }
  return actions;
}

function buildMetadataPayload(
  fields: UploadFieldDefinition[],
  values: Record<string, string>,
): Record<string, unknown> {
  const metadata: Record<string, unknown> = {};
  fields.forEach((field) => {
    const rawValue = values[field.name];
    if (rawValue === undefined || rawValue === '') {
      return;
    }
    switch (field.type) {
      case 'number': {
        const parsed = Number(rawValue);
        metadata[field.name] = Number.isNaN(parsed) ? rawValue : parsed;
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
