export type AuditEvent = {
  id: string;
  action: string;
  actor: string;
  timestamp: string;
  details?: string;
};

type Props = {
  events: AuditEvent[];
};

export default function AuditTimeline({ events }: Props) {
  if (events.length === 0) {
    return <p className="text-sm text-gray-500 dark:text-slate-400">No audit events recorded.</p>;
  }

  return (
    <ol className="space-y-2">
      {events.map((event) => (
        <li
          key={event.id}
          className="rounded border border-gray-200 bg-white p-3 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900"
        >
          <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">{event.action}</p>
          <p className="text-xs text-slate-500 dark:text-slate-400">{event.actor}</p>
          <p className="text-xs text-slate-400 dark:text-slate-500">{event.timestamp}</p>
          {event.details ? <p className="mt-2 text-xs text-slate-600 dark:text-slate-300">{event.details}</p> : null}
        </li>
      ))}
    </ol>
  );
}
