const STATUS_STYLES: Record<string, { label: string; className: string }> = {
  DRAFT: { label: 'Draft', className: 'bg-gray-200 text-gray-800 dark:bg-gray-700 dark:text-gray-100' },
  SUBMITTED: { label: 'Submitted', className: 'bg-blue-200 text-blue-800 dark:bg-blue-500/30 dark:text-blue-100' },
  UNDER_REVIEW: { label: 'Under Review', className: 'bg-yellow-200 text-yellow-800 dark:bg-yellow-500/30 dark:text-yellow-100' },
  REWORK: { label: 'Rework', className: 'bg-orange-200 text-orange-800 dark:bg-orange-500/30 dark:text-orange-100' },
  APPROVED: { label: 'Approved', className: 'bg-green-200 text-green-800 dark:bg-green-500/30 dark:text-green-100' },
  CLOSED: { label: 'Closed', className: 'bg-purple-200 text-purple-800 dark:bg-purple-500/30 dark:text-purple-100' },
};

type Props = {
  status: string;
};

export default function StatusBadge({ status }: Props) {
  const normalized = status?.toUpperCase?.() ?? '';
  const style =
    STATUS_STYLES[normalized] ?? {
      label: status,
      className: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-200',
    };
  return <span className={`rounded px-2 py-1 text-xs font-semibold transition-colors ${style.className}`}>{style.label}</span>;
}
