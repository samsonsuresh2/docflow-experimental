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
    return <p className="text-sm text-gray-500">No audit events recorded.</p>;
  }

  return (
    <ol className="space-y-2">
      {events.map((event) => (
        <li key={event.id} className="rounded border border-gray-200 bg-white p-3 shadow-sm">
          <p className="text-sm font-semibold text-slate-800">{event.action}</p>
          <p className="text-xs text-slate-500">{event.actor}</p>
          <p className="text-xs text-slate-400">{event.timestamp}</p>
          {event.details ? <p className="mt-2 text-xs text-slate-600">{event.details}</p> : null}
        </li>
      ))}
    </ol>
  );
}
