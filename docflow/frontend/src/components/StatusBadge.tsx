const STATUS_STYLES: Record<string, { label: string; className: string }> = {
  DRAFT: { label: 'Draft', className: 'bg-gray-200 text-gray-800' },
  SUBMITTED: { label: 'Submitted', className: 'bg-blue-200 text-blue-800' },
  UNDER_REVIEW: { label: 'Under Review', className: 'bg-yellow-200 text-yellow-800' },
  REWORK: { label: 'Rework', className: 'bg-orange-200 text-orange-800' },
  APPROVED: { label: 'Approved', className: 'bg-green-200 text-green-800' },
  CLOSED: { label: 'Closed', className: 'bg-purple-200 text-purple-800' },
};

type Props = {
  status: string;
};

export default function StatusBadge({ status }: Props) {
  const normalized = status?.toUpperCase?.() ?? '';
  const style = STATUS_STYLES[normalized] ?? { label: status, className: 'bg-gray-100 text-gray-700' };
  return <span className={`rounded px-2 py-1 text-xs font-semibold ${style.className}`}>{style.label}</span>;
}
