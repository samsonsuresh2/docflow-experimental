export type DocumentStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'REWORK'
  | 'APPROVED'
  | 'REJECTED'
  | 'CLOSED';

const LEGACY_STATUS_MAP: Record<string, DocumentStatus> = {
  OPEN: 'SUBMITTED',
};

const VALID_STATUSES: DocumentStatus[] = [
  'DRAFT',
  'SUBMITTED',
  'UNDER_REVIEW',
  'REWORK',
  'APPROVED',
  'REJECTED',
  'CLOSED',
];

export function normalizeStatus(status: string | null | undefined): DocumentStatus | null {
  if (!status) {
    return null;
  }
  const upper = status.toUpperCase();
  if (upper in LEGACY_STATUS_MAP) {
    return LEGACY_STATUS_MAP[upper];
  }
  if (VALID_STATUSES.includes(upper as DocumentStatus)) {
    return upper as DocumentStatus;
  }
  return null;
}
