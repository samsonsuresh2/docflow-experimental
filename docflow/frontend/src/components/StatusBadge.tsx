const COLORS: Record<string, string> = {
  Draft: 'bg-gray-200 text-gray-800',
  Submitted: 'bg-blue-200 text-blue-800',
  'Under Review': 'bg-yellow-200 text-yellow-800',
  Rework: 'bg-orange-200 text-orange-800',
  Approved: 'bg-green-200 text-green-800',
  Closed: 'bg-purple-200 text-purple-800'
};

type Props = {
  status: keyof typeof COLORS;
};

export default function StatusBadge({ status }: Props) {
  const colorClass = COLORS[status] ?? 'bg-gray-100 text-gray-700';
  return <span className={`rounded px-2 py-1 text-xs font-semibold ${colorClass}`}>{status}</span>;
}
