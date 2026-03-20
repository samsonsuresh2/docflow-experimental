import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from 'react';
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
  const [releaseText, setReleaseText] = useState('');
  const [releasePanelOpen, setReleasePanelOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [promoting, setPromoting] = useState(false);
  const [releasing, setReleasing] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [releaseStatusMessage, setReleaseStatusMessage] = useState<string | null>(null);
  const [releaseErrorMessage, setReleaseErrorMessage] = useState<string | null>(null);
  const [uploadStatus, setUploadStatus] = useState<UploadSchemaStatusResponse | null>(null);

  const isUploadView = activeConfig === 'upload';
  const isSandboxMode = uploadStatus?.bindingStrategy === 'SANDBOX_ONLY';
  const isActiveOnlyMode = uploadStatus?.bindingStrategy === 'ACTIVE_ONLY';
  const uploadReadonly = isUploadView && isActiveOnlyMode;
  const nextActiveVersion = (uploadStatus?.activeVersion ?? 0) + 1;

  const currentOption = useMemo(
    () =>
      activeConfig === 'upload'
        ? { label: 'Upload Screen Config', endpoint: '/admin/config/upload', defaultTemplate: UPLOAD_DEFAULT_TEMPLATE }
        : { label: 'Review Filter Config', endpoint: '/admin/config/review-filters', defaultTemplate: REVIEW_FILTER_DEFAULT_TEMPLATE },
    [activeConfig],
  );

  const formatJson = (raw: string | null, fallback: string) =>
    raw ? JSON.stringify(JSON.parse(raw), null, 2) : JSON.stringify(JSON.parse(fallback), null, 2);

  const loadConfig = async () => {
    try {
      setLoading(true);
      if (isUploadView) {
        const response = await api.get<UploadSchemaStatusResponse>(currentOption.endpoint);
        setUploadStatus(response.data);
        const formatted = formatJson(response.data.configJson, currentOption.defaultTemplate);
        setConfigText(formatted);
        if (response.data.bindingStrategy === 'ACTIVE_ONLY') {
          setReleaseText(formatted);
        }
      } else {
        const response = await api.get<ConfigResponse>(currentOption.endpoint);
        setConfigText(formatJson(response.data.configJson, currentOption.defaultTemplate));
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
      setErrorMessage('Current active schema is immutable in ACTIVE_ONLY mode.');
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

  const handleValidateRelease = () => {
    setReleaseStatusMessage(null);
    setReleaseErrorMessage(null);
    try {
      const parsed = JSON.parse(releaseText) as unknown;
      const isSupportedShape =
        Array.isArray(parsed) ||
        (parsed !== null && typeof parsed === 'object' && Array.isArray((parsed as { fields?: unknown }).fields));
      if (!isSupportedShape) {
        setReleaseErrorMessage('Upload schema JSON must be an array of fields or an object containing a fields array.');
        return;
      }
      setReleaseText(JSON.stringify(parsed, null, 2));
      setReleaseStatusMessage('Schema JSON is valid and ready to release.');
    } catch {
      setReleaseErrorMessage('Schema JSON must be valid JSON before release.');
    }
  };

  const handleReleaseFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setReleaseStatusMessage(null);
    setReleaseErrorMessage(null);
    try {
      const parsed = JSON.parse(await file.text());
      setReleaseText(JSON.stringify(parsed, null, 2));
      setReleaseStatusMessage(`Loaded ${file.name}. Review and validate before release.`);
    } catch {
      setReleaseErrorMessage('Uploaded file must contain valid JSON.');
    } finally {
      event.target.value = '';
    }
  };

  const handleRelease = async () => {
    if (!window.confirm(`Release schema as active version v${nextActiveVersion}?`)) {
      return;
    }
    setStatusMessage(null);
    setErrorMessage(null);
    setReleaseStatusMessage(null);
    setReleaseErrorMessage(null);
    try {
      const parsed = JSON.parse(releaseText);
      setReleasing(true);
      const response = await api.post<UploadSchemaStatusResponse>('/admin/config/upload/release', {
        configJson: JSON.stringify(parsed),
      });
      setUploadStatus(response.data);
      setConfigText(JSON.stringify(parsed, null, 2));
      setReleaseText(JSON.stringify(parsed, null, 2));
      setReleasePanelOpen(false);
      setStatusMessage(`New active schema version v${response.data.activeVersion} released successfully.`);
      await loadConfig();
    } catch (error) {
      const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setReleaseErrorMessage(message ?? 'Unable to release new active schema version.');
    } finally {
      setReleasing(false);
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
            {isActiveOnlyMode ? <div>Next Release Target: v{nextActiveVersion}</div> : null}
            {uploadStatus.updatedAt ? <div>Last Updated: {new Date(uploadStatus.updatedAt).toLocaleString()}</div> : null}
          </div>
        ) : null}
      </div>
      <form className="space-y-4 rounded border border-slate-200 bg-white p-6 shadow-sm" onSubmit={handleSave}>
        {uploadReadonly ? (
          <p className="text-sm text-amber-700">
            Current active schema is immutable in ACTIVE_ONLY. Use the release flow below to import and release the next approved version.
          </p>
        ) : null}
        <textarea rows={18} className="mt-1 w-full rounded border border-slate-300 px-3 py-2 font-mono text-sm" value={configText} onChange={(e) => setConfigText(e.target.value)} disabled={loading || saving || uploadReadonly} />
        <div className="flex flex-wrap gap-2">
          <button type="submit" disabled={loading || saving || uploadReadonly} className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white">
            {saving ? 'Saving...' : 'Save Configuration'}
          </button>
          {isUploadView && isSandboxMode ? (
            <button type="button" onClick={handlePromote} disabled={loading || promoting} className="rounded bg-emerald-600 px-4 py-2 text-sm font-semibold text-white">
              {promoting ? 'Promoting...' : 'Promote to Active (Release)'}
            </button>
          ) : null}
        </div>
        {loading ? <p className="text-sm text-slate-500">Loading configuration...</p> : null}
        {errorMessage ? <p className="text-sm text-red-600">{errorMessage}</p> : null}
        {statusMessage ? <p className="text-sm text-green-600">{statusMessage}</p> : null}
      </form>
      {isUploadView && isActiveOnlyMode ? (
        <section className="space-y-4 rounded border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="space-y-1">
              <h2 className="text-lg font-semibold text-slate-800">Release New Active Version</h2>
              <p className="text-sm text-slate-600">
                Current active version is immutable. Use this flow to release the next approved version.
              </p>
            </div>
            <button
              type="button"
              onClick={() => {
                setReleasePanelOpen((open) => !open);
                setReleaseStatusMessage(null);
                setReleaseErrorMessage(null);
              }}
              className="rounded bg-slate-800 px-4 py-2 text-sm font-semibold text-white"
            >
              {releasePanelOpen ? 'Hide Release Panel' : 'Release New Version'}
            </button>
          </div>
          {releasePanelOpen ? (
            <div className="space-y-4 border-t border-slate-200 pt-4">
              <label className="block text-sm font-medium text-slate-700">
                Approved Schema JSON
                <textarea
                  rows={16}
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2 font-mono text-sm"
                  value={releaseText}
                  onChange={(e) => setReleaseText(e.target.value)}
                  disabled={loading || releasing}
                />
              </label>
              <label className="block text-sm text-slate-700">
                Or load from JSON file
                <input type="file" accept=".json,application/json" className="mt-1 block w-full text-sm" onChange={handleReleaseFileChange} disabled={loading || releasing} />
              </label>
              <div className="flex flex-wrap gap-2">
                <button type="button" onClick={handleValidateRelease} disabled={loading || releasing} className="rounded bg-amber-500 px-4 py-2 text-sm font-semibold text-white">
                  Validate JSON
                </button>
                <button type="button" onClick={handleRelease} disabled={loading || releasing} className="rounded bg-emerald-600 px-4 py-2 text-sm font-semibold text-white">
                  {releasing ? 'Releasing...' : `Release Active v${nextActiveVersion}`}
                </button>
              </div>
              {releaseErrorMessage ? <p className="text-sm text-red-600">{releaseErrorMessage}</p> : null}
              {releaseStatusMessage ? <p className="text-sm text-green-600">{releaseStatusMessage}</p> : null}
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

function AuthRequired({ message }: { message: string }) {
  return <div className="rounded border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700">{message}</div>;
}
