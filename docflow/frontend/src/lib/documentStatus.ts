export type DocumentStatus =
  | 'DRAFT'
  | 'OPEN'
  | 'UNDER_REVIEW'
  | 'ON_HOLD'
  | 'REWORK'
  | 'APPROVED'
  | 'REJECTED'
  | 'CLOSED';

const VALID_STATUSES: DocumentStatus[] = [
  'DRAFT',
  'OPEN',
  'UNDER_REVIEW',
  'ON_HOLD',
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
  if (VALID_STATUSES.includes(upper as DocumentStatus)) {
    return upper as DocumentStatus;
  }
  return null;
}
