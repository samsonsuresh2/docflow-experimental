import { FormEvent, useEffect, useMemo, useState } from 'react';
import api from '../lib/api';
import { useUser } from '../lib/UserContext';

interface ConfigResponse {
  configJson: string | null;
}

type AdminConfigView = 'upload' | 'reviewFilters';

const UPLOAD_DEFAULT_TEMPLATE = `[
  {
    "name": "customerId",
    "label": "Customer ID",
    "type": "text",
    "required": true
  }
]`;

const REVIEW_FILTER_DEFAULT_TEMPLATE = `[
  {
    "key": "uploaded_date",
    "type": "date",
    "label": "Uploaded After",
    "source": "DOCUMENT_PARENT"
  },
  {
    "key": "customer_id",
    "type": "text",
    "label": "Customer ID",
    "source": "META_DATA"
  },
  {
    "key": "branch_code",
    "type": "dropdown",
    "label": "Branch",
    "source": "META_DATA",
    "options": ["BR001", "BR002", "BR003"]
  }
]`;

const ADMIN_CONFIG_OPTIONS: Record<AdminConfigView, { label: string; description: string; endpoint: string; defaultTemplate: string }> = {
  upload: {
    label: 'Upload Screen Config',
    description:
      'Define dynamic metadata fields in JSON. Each field supports name, label, type, required, optional options for selects, and roles to restrict visibility.',
    endpoint: '/admin/config/upload',
    defaultTemplate: UPLOAD_DEFAULT_TEMPLATE,
  },
  reviewFilters: {
    label: 'Review Filter Config',
    description:
      'Configure the dynamic filters shown on the Review screen. Provide key, label, type, and source (DOCUMENT_PARENT or META_DATA); dropdown filters may include an options array.',
    endpoint: '/admin/config/review-filters',
    defaultTemplate: REVIEW_FILTER_DEFAULT_TEMPLATE,
  },
};

export default function Admin() {
  const { user } = useUser();
  const [activeConfig, setActiveConfig] = useState<AdminConfigView>('upload');
  const [configText, setConfigText] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const currentOption = useMemo(() => ADMIN_CONFIG_OPTIONS[activeConfig], [activeConfig]);

  useEffect(() => {
    if (!user || user.role !== 'ADMIN') {
      return;
    }

    let isMounted = true;
    const loadConfig = async () => {
      try {
        setLoading(true);
        const response = await api.get<ConfigResponse>(currentOption.endpoint);
        if (!isMounted) {
          return;
        }
        const raw = response.data.configJson;
        if (raw) {
          try {
            setConfigText(JSON.stringify(JSON.parse(raw), null, 2));
          } catch {
            setConfigText(raw);
          }
        } else {
          setConfigText(JSON.stringify(JSON.parse(currentOption.defaultTemplate), null, 2));
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage('Failed to load configuration.');
          setConfigText(JSON.stringify(JSON.parse(currentOption.defaultTemplate), null, 2));
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    loadConfig();

    return () => {
      isMounted = false;
    };
  }, [currentOption, user]);

  if (!user) {
    return <AuthRequired message="Please sign in to administer configuration." />;
  }

  if (user.role !== 'ADMIN') {
    return <AuthRequired message="Only administrators can manage configuration." />;
  }

  const handleSave = async (event: FormEvent) => {
    event.preventDefault();
    setStatusMessage(null);
    setErrorMessage(null);

    try {
      const parsed = JSON.parse(configText);
      const formatted = JSON.stringify(parsed);
      setSaving(true);
      await api.post(currentOption.endpoint, { configJson: formatted });
      setStatusMessage('Configuration saved successfully.');
      setConfigText(JSON.stringify(parsed, null, 2));
    } catch (error) {
      if (error instanceof SyntaxError) {
        setErrorMessage('Configuration must be valid JSON.');
      } else {
        setErrorMessage('Unable to save configuration.');
      }
    } finally {
      setSaving(false);
    }
  };

  const handlePrettify = () => {
    try {
      const parsed = JSON.parse(configText);
      setConfigText(JSON.stringify(parsed, null, 2));
      setStatusMessage('Configuration formatted.');
      setErrorMessage(null);
    } catch (error) {
      setErrorMessage('Unable to format invalid JSON.');
    }
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-xl font-semibold text-slate-800 dark:text-slate-100">{currentOption.label}</h1>
            <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">{currentOption.description}</p>
          </div>
          <div className="flex flex-col text-sm">
            <label
              htmlFor="config-view"
              className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300"
            >
              Configuration
            </label>
            <select
              id="config-view"
              className="mt-1 rounded border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
              value={activeConfig}
              onChange={(event) => {
                setActiveConfig(event.target.value as AdminConfigView);
                setStatusMessage(null);
                setErrorMessage(null);
              }}
              disabled={loading || saving}
            >
              <option value="upload">Upload Screen Config</option>
              <option value="reviewFilters">Review Filter Config</option>
            </select>
          </div>
        </div>
      </div>
      <form
        className="space-y-4 rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900"
        onSubmit={handleSave}
      >
        <label className="block text-sm">
          <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
            Configuration JSON
          </span>
          <textarea
            rows={18}
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2 font-mono text-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
            value={configText}
            onChange={(event) => setConfigText(event.target.value)}
            disabled={loading || saving}
          />
        </label>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="inline-flex items-center rounded border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-100 disabled:cursor-not-allowed dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800/80"
            onClick={handlePrettify}
            disabled={loading || saving}
          >
            Prettify JSON
          </button>
          <button
            type="submit"
            className="inline-flex items-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300 dark:bg-blue-500 dark:hover:bg-blue-400 dark:disabled:bg-blue-400/50"
            disabled={loading || saving}
          >
            {saving ? 'Saving…' : 'Save Configuration'}
          </button>
        </div>
        {loading ? <p className="text-sm text-slate-500 dark:text-slate-300">Loading configuration…</p> : null}
        {errorMessage ? <p className="text-sm text-red-600 dark:text-red-400">{errorMessage}</p> : null}
        {statusMessage ? <p className="text-sm text-green-600 dark:text-green-400">{statusMessage}</p> : null}
      </form>
    </div>
  );
}

function AuthRequired({ message }: { message: string }) {
  return (
    <div className="rounded border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-300">
      {message}
    </div>
  );
}
