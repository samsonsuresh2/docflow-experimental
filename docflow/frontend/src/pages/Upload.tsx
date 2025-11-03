import { FormEvent, useEffect, useMemo, useState } from 'react';
import DynamicForm from '../components/DynamicForm';
import api from '../lib/api';
import { fieldsForRole, parseUploadFieldConfig, UploadFieldDefinition } from '../lib/config';
import { DynamicFormValues, isDynamicFormValueEmpty } from '../lib/dynamicFormValues';
import { useUser } from '../lib/UserContext';
import type { DocumentResponse } from '../types/documents';

interface ConfigResponse {
  configJson: string | null;
}

export default function Upload() {
  const { user } = useUser();
  const [loadingConfig, setLoadingConfig] = useState(false);
  const [fields, setFields] = useState<UploadFieldDefinition[]>([]);
  const [metadataValues, setMetadataValues] = useState<DynamicFormValues>({});
  const [documentNumber, setDocumentNumber] = useState('');
  const [title, setTitle] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [activeAction, setActiveAction] = useState<'save' | 'submit' | null>(null);

  useEffect(() => {
    let isMounted = true;
    const loadConfig = async () => {
      try {
        setLoadingConfig(true);
        const response = await api.get<ConfigResponse>('/config/upload-fields');
        if (!isMounted) {
          return;
        }
        const parsed = parseUploadFieldConfig(response.data.configJson);
        setFields(parsed);
      } catch (error) {
        if (isMounted) {
          setErrorMessage('Unable to load upload field configuration.');
        }
      } finally {
        if (isMounted) {
          setLoadingConfig(false);
        }
      }
    };

    loadConfig();

    return () => {
      isMounted = false;
    };
  }, []);

  const availableFields = useMemo(
    () => fieldsForRole(fields, user?.role).map((field) => ({ ...field, readOnly: false })),
    [fields, user?.role],
  );

  if (!user) {
    return <AuthRequired />;
  }

  const performUpload = async (intent: 'save' | 'submit') => {
    setStatusMessage(null);
    setErrorMessage(null);

    if (!documentNumber.trim() || !title.trim()) {
      setErrorMessage('Document number and title are required.');
      return;
    }

    try {
      setSubmitting(true);
      setActiveAction(intent);
      const metadata = buildMetadataPayload(availableFields, metadataValues);
      const payload = {
        documentNumber: documentNumber.trim(),
        title: title.trim(),
        metadata,
      };

      const formData = new FormData();
      formData.append('metadata', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
      if (file) {
        formData.append('file', file);
      }

      const response = await api.post<DocumentResponse>('/documents/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      if (intent === 'submit') {
        try {
          await api.put(`/documents/${response.data.id}/submit`);
          setStatusMessage(
            `Document submitted and moved to Open status. Document ID: ${response.data.id}.`,
          );
        } catch (error) {
          setErrorMessage('Document saved as draft, but submission failed. Please submit from the Review page.');
          return;
        }
      } else {
        setStatusMessage('Document saved as a draft.');
      }

      setDocumentNumber('');
      setTitle('');
      setMetadataValues({});
      setFile(null);
    } catch (error) {
      setErrorMessage('Upload failed. Please verify your inputs and try again.');
    } finally {
      setSubmitting(false);
      setActiveAction(null);
    }
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    await performUpload('save');
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <h1 className="text-xl font-semibold text-slate-800 dark:text-slate-100">Upload a new document</h1>
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
          Supply document identifiers and capture metadata using the dynamic upload configuration. The selected user will be
          stored on each record via the <code className="rounded bg-slate-100 px-1 dark:bg-slate-800">X-USER-ID</code> header.
        </p>
      </div>
      <form
        className="space-y-6 rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900"
        onSubmit={handleSubmit}
      >
        <fieldset className="space-y-4" disabled={submitting}>
          <legend className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-300">Document Details</legend>
          <label className="block text-sm">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">Document Number</span>
            <input
              type="text"
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
              value={documentNumber}
              onChange={(event) => setDocumentNumber(event.target.value)}
              required
            />
          </label>
          <label className="block text-sm">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">Title</span>
            <input
              type="text"
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              required
            />
          </label>
          <label className="block text-sm">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">File</span>
            <input
              type="file"
              className="mt-1 block w-full text-sm text-slate-600 dark:text-slate-300"
              onChange={(event) => setFile(event.target.files ? event.target.files[0] : null)}
            />
          </label>
        </fieldset>
        <fieldset className="space-y-4" disabled={submitting}>
          <legend className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-300">Metadata</legend>
          {loadingConfig ? (
            <p className="text-sm text-slate-500 dark:text-slate-300">Loading metadata fields…</p>
          ) : availableFields.length === 0 ? (
            <p className="text-sm text-slate-500 dark:text-slate-300">No dynamic metadata fields defined yet.</p>
          ) : (
            <DynamicForm
              fields={availableFields}
              initialValues={metadataValues}
              onChange={(values) => setMetadataValues(values)}
              submitLabel={null}
              disabled={submitting || loadingConfig}
            />
          )}
        </fieldset>
        {errorMessage ? <p className="text-sm text-red-600 dark:text-red-400">{errorMessage}</p> : null}
        {statusMessage ? <p className="text-sm text-green-600 dark:text-green-400">{statusMessage}</p> : null}
        <div className="flex flex-wrap gap-2">
          <button
            type="submit"
            disabled={submitting}
            className="inline-flex items-center rounded bg-slate-700 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400 dark:bg-slate-600 dark:hover:bg-slate-500 dark:disabled:bg-slate-700/60"
          >
            {submitting && activeAction === 'save' ? 'Saving…' : 'Save Draft'}
          </button>
          <button
            type="button"
            disabled={submitting}
            onClick={() => performUpload('submit')}
            className="inline-flex items-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300 dark:bg-blue-500 dark:hover:bg-blue-400 dark:disabled:bg-blue-400/50"
          >
            {submitting && activeAction === 'submit' ? 'Submitting…' : 'Submit to Open'}
          </button>
        </div>
      </form>
    </div>
  );
}

function AuthRequired() {
  return (
    <div className="rounded border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-300">
      Please sign in via the Login page to upload documents.
    </div>
  );
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
