import { FormEvent, useEffect, useState } from 'react';
import api from '../lib/api';
import { useUser } from '../lib/UserContext';

interface ConfigResponse {
  configJson: string | null;
}

export default function Admin() {
  const { user } = useUser();
  const [configText, setConfigText] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!user || user.role !== 'ADMIN') {
      return;
    }
    let isMounted = true;
    const loadConfig = async () => {
      try {
        setLoading(true);
        const response = await api.get<ConfigResponse>('/config/upload-fields');
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
          setConfigText('[\n  {\n    "name": "customerId",\n    "label": "Customer ID",\n    "type": "text",\n    "required": true\n  }\n]');
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage('Failed to load upload configuration.');
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
  }, [user]);

  if (!user) {
    return <AuthRequired message="Please sign in to administer configuration." />;
  }

  if (user.role !== 'ADMIN') {
    return <AuthRequired message="Only administrators can manage upload field configuration." />;
  }

  const handleSave = async (event: FormEvent) => {
    event.preventDefault();
    setStatusMessage(null);
    setErrorMessage(null);

    try {
      const parsed = JSON.parse(configText);
      const formatted = JSON.stringify(parsed);
      setSaving(true);
      await api.post('/config/upload-fields', { configJson: formatted });
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
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-800">Upload field configuration</h1>
        <p className="mt-2 text-sm text-slate-600">
          Define dynamic metadata fields in JSON. Each field supports <code>name</code>, <code>label</code>, <code>type</code>,
          <code>required</code>, optional <code>options</code> for selects, and <code>roles</code> to restrict visibility.
        </p>
      </div>
      <form className="space-y-4 rounded border border-slate-200 bg-white p-6 shadow-sm" onSubmit={handleSave}>
        <label className="block text-sm">
          <span className="text-xs font-semibold uppercase tracking-wide text-slate-600">Configuration JSON</span>
          <textarea
            rows={18}
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2 font-mono text-sm focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200"
            value={configText}
            onChange={(event) => setConfigText(event.target.value)}
            disabled={loading || saving}
          />
        </label>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="inline-flex items-center rounded border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-100"
            onClick={handlePrettify}
            disabled={loading || saving}
          >
            Prettify JSON
          </button>
          <button
            type="submit"
            className="inline-flex items-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300"
            disabled={loading || saving}
          >
            {saving ? 'Saving…' : 'Save Configuration'}
          </button>
        </div>
        {loading ? <p className="text-sm text-slate-500">Loading configuration…</p> : null}
        {errorMessage ? <p className="text-sm text-red-600">{errorMessage}</p> : null}
        {statusMessage ? <p className="text-sm text-green-600">{statusMessage}</p> : null}
      </form>
    </div>
  );
}

function AuthRequired({ message }: { message: string }) {
  return (
    <div className="rounded border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700">{message}</div>
  );
}
