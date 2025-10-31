export type DocumentStatus = 'DRAFT' | 'OPEN' | 'UNDER_REVIEW' | 'APPROVED' | 'CLOSED';

const LEGACY_STATUS_MAP: Record<string, DocumentStatus> = {
  SUBMITTED: 'OPEN',
  REWORK: 'OPEN',
};

const VALID_STATUSES: DocumentStatus[] = ['DRAFT', 'OPEN', 'UNDER_REVIEW', 'APPROVED', 'CLOSED'];

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
