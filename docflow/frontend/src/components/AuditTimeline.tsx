export type AuditEvent = {
  id: string;
  action: string;
  actor: string;
  timestamp: string;
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
        <li key={event.id} className="rounded border border-gray-200 p-3">
          <p className="text-sm font-semibold">{event.action}</p>
          <p className="text-xs text-gray-500">{event.actor}</p>
          <p className="text-xs text-gray-400">{event.timestamp}</p>
        </li>
      ))}
    </ol>
  );
}
