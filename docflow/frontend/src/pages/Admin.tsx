import { FormEvent, useEffect, useMemo, useState } from 'react';
import api from '../lib/api';
import { useUser } from '../lib/UserContext';

interface ConfigResponse {
  configJson: string | null;
}

interface UploadSchemaStatusResponse {
  bindingStrategy: 'SANDBOX_ONLY' | 'ACTIVE_ONLY';
  activeVersion: number | null;
  sandboxVersion: number;
  configJson: string | null;
  updatedBy: string | null;
  updatedAt: string | null;
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
  }
]`;

export default function Admin() {
  const { user } = useUser();
  const [activeConfig, setActiveConfig] = useState<AdminConfigView>('upload');
  const [configText, setConfigText] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [promoting, setPromoting] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [uploadStatus, setUploadStatus] = useState<UploadSchemaStatusResponse | null>(null);

  const isUploadView = activeConfig === 'upload';
  const isSandboxMode = uploadStatus?.bindingStrategy === 'SANDBOX_ONLY';
  const uploadReadonly = isUploadView && uploadStatus?.bindingStrategy === 'ACTIVE_ONLY';

  const currentOption = useMemo(
    () =>
      activeConfig === 'upload'
        ? { label: 'Upload Screen Config', endpoint: '/admin/config/upload', defaultTemplate: UPLOAD_DEFAULT_TEMPLATE }
        : { label: 'Review Filter Config', endpoint: '/admin/config/review-filters', defaultTemplate: REVIEW_FILTER_DEFAULT_TEMPLATE },
    [activeConfig],
  );

  const loadConfig = async () => {
    try {
      setLoading(true);
      if (isUploadView) {
        const response = await api.get<UploadSchemaStatusResponse>(currentOption.endpoint);
        setUploadStatus(response.data);
        const raw = response.data.configJson;
        setConfigText(raw ? JSON.stringify(JSON.parse(raw), null, 2) : JSON.stringify(JSON.parse(currentOption.defaultTemplate), null, 2));
      } else {
        const response = await api.get<ConfigResponse>(currentOption.endpoint);
        const raw = response.data.configJson;
        setConfigText(raw ? JSON.stringify(JSON.parse(raw), null, 2) : JSON.stringify(JSON.parse(currentOption.defaultTemplate), null, 2));
      }
    } catch {
      setErrorMessage('Failed to load configuration.');
      setConfigText(JSON.stringify(JSON.parse(currentOption.defaultTemplate), null, 2));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!user || user.role !== 'ADMIN') {
      return;
    }
    loadConfig();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentOption, user]);

  if (!user) return <AuthRequired message="Please sign in to administer configuration." />;
  if (user.role !== 'ADMIN') return <AuthRequired message="Only administrators can manage configuration." />;

  const handleSave = async (event: FormEvent) => {
    event.preventDefault();
    setStatusMessage(null);
    setErrorMessage(null);
    if (uploadReadonly) {
      setErrorMessage('Editing is disabled while server is in ACTIVE mode.');
      return;
    }
    try {
      const parsed = JSON.parse(configText);
      setSaving(true);
      await api.post(currentOption.endpoint, { configJson: JSON.stringify(parsed) });
      setStatusMessage('Configuration saved successfully.');
      setConfigText(JSON.stringify(parsed, null, 2));
      if (isUploadView) {
        await loadConfig();
      }
    } catch (error) {
      if (error instanceof SyntaxError) setErrorMessage('Configuration must be valid JSON.');
      else setErrorMessage('Unable to save configuration.');
    } finally {
      setSaving(false);
    }
  };

  const handlePromote = async () => {
    if (!window.confirm('Promote current sandbox schema as a new active release?')) {
      return;
    }
    setStatusMessage(null);
    setErrorMessage(null);
    try {
      setPromoting(true);
      await api.post('/admin/config/upload/promote');
      setStatusMessage('Sandbox schema promoted to active version.');
      await loadConfig();
    } catch {
      setErrorMessage('Unable to promote sandbox schema.');
    } finally {
      setPromoting(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <h1 className="text-xl font-semibold text-slate-800">{currentOption.label}</h1>
          <select id="config-view" value={activeConfig} onChange={(e) => setActiveConfig(e.target.value as AdminConfigView)} disabled={loading || saving}>
            <option value="upload">Upload Screen Config</option>
            <option value="reviewFilters">Review Filter Config</option>
          </select>
        </div>
        {isUploadView && uploadStatus ? (
          <div className="mt-4 space-y-2 text-sm text-slate-700">
            <div className="font-semibold">
              {uploadStatus.bindingStrategy === 'SANDBOX_ONLY' ? 'SANDBOX MODE (Draft)' : 'ACTIVE MODE (Released)'}
            </div>
            <div>Sandbox Version: v{uploadStatus.sandboxVersion}</div>
            <div>Active Version: {uploadStatus.activeVersion ? `v${uploadStatus.activeVersion}` : 'Not promoted yet'}</div>
            {uploadStatus.updatedAt ? <div>Last Updated: {new Date(uploadStatus.updatedAt).toLocaleString()}</div> : null}
          </div>
        ) : null}
      </div>
      <form className="space-y-4 rounded border border-slate-200 bg-white p-6 shadow-sm" onSubmit={handleSave}>
        {uploadReadonly ? <p className="text-sm text-amber-700">Editing is disabled because server mode is ACTIVE_ONLY.</p> : null}
        <textarea rows={18} className="mt-1 w-full rounded border border-slate-300 px-3 py-2 font-mono text-sm" value={configText} onChange={(e) => setConfigText(e.target.value)} disabled={loading || saving || uploadReadonly} />
        <div className="flex flex-wrap gap-2">
          <button type="submit" disabled={loading || saving || uploadReadonly} className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white">
            {saving ? 'Saving…' : 'Save Configuration'}
          </button>
          {isUploadView && isSandboxMode ? (
            <button type="button" onClick={handlePromote} disabled={loading || promoting} className="rounded bg-emerald-600 px-4 py-2 text-sm font-semibold text-white">
              {promoting ? 'Promoting…' : 'Promote to Active (Release)'}
            </button>
          ) : null}
        </div>
        {loading ? <p className="text-sm text-slate-500">Loading configuration…</p> : null}
        {errorMessage ? <p className="text-sm text-red-600">{errorMessage}</p> : null}
        {statusMessage ? <p className="text-sm text-green-600">{statusMessage}</p> : null}
      </form>
    </div>
  );
}

function AuthRequired({ message }: { message: string }) {
  return <div className="rounded border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700">{message}</div>;
}
