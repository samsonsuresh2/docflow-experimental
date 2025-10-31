import { FormEvent, useState } from 'react';
import AuditTimeline, { type AuditEvent } from '../components/AuditTimeline';
import api from '../lib/api';
import { useUser } from '../lib/UserContext';

interface AuditEntryResponse {
  fieldKey: string;
  oldValue: unknown;
  newValue: unknown;
  changeType: string;
  changedBy: string;
  changedAt: string;
}

export default function Audit() {
  const { user } = useUser();
  const [documentIdInput, setDocumentIdInput] = useState('');
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  if (!user) {
    return <AuthRequired />;
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setErrorMessage(null);
    const id = Number(documentIdInput.trim());
    if (!documentIdInput.trim() || Number.isNaN(id)) {
      setErrorMessage('Please enter a numeric document ID.');
      return;
    }

    try {
      setLoading(true);
      const response = await api.get<AuditEntryResponse[]>(`/documents/${id}/audit`);
      const mapped = response.data.map<AuditEvent>((entry, index) => ({
        id: `${entry.fieldKey}-${index}`,
        action: `${entry.changeType} · ${entry.fieldKey}`,
        actor: entry.changedBy,
        timestamp: new Date(entry.changedAt).toLocaleString(),
        details: buildDetails(entry),
      }));
      setEvents(mapped);
    } catch (error) {
      setEvents([]);
      setErrorMessage('Unable to load audit trail for this document.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <h1 className="text-xl font-semibold text-slate-800 dark:text-slate-100">Audit trail</h1>
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
          Inspect field-level audit events captured during document progression. Search using a document ID to load the
          timeline.
        </p>
        <form className="mt-4 flex flex-wrap gap-2" onSubmit={handleSubmit}>
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
            disabled={loading}
          >
            {loading ? 'Loading…' : 'Load Audit Trail'}
          </button>
        </form>
      </div>
      {errorMessage ? <p className="text-sm text-red-600 dark:text-red-400">{errorMessage}</p> : null}
      <AuditTimeline events={events} />
    </div>
  );
}

function buildDetails(entry: AuditEntryResponse): string {
  const fromValue = formatValue(entry.oldValue);
  const toValue = formatValue(entry.newValue);
  return `From: ${fromValue} → To: ${toValue}`;
}

function formatValue(value: unknown): string {
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

function AuthRequired() {
  return (
    <div className="rounded border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-300">
      Please sign in via the Login page to view the audit trail.
    </div>
  );
}
