import { describe, expect, it } from 'vitest';
import { buildReviewSearchParams } from './Review';

describe('buildReviewSearchParams', () => {
  it('uses the status filter value directly in the request params', () => {
    const params = buildReviewSearchParams({
      statusFilter: 'OPEN',
      documentId: 'DOC-100',
      page: 0,
      size: 10,
      sortBy: 'id',
      direction: 'asc',
      filtersPayload: {},
    });

    expect(params).toMatchObject({
      status: 'OPEN',
      id: 'DOC-100',
      page: 0,
      size: 10,
      sortBy: 'id',
      direction: 'asc',
    });
  });

  it('omits status when ALL is selected', () => {
    const params = buildReviewSearchParams({
      statusFilter: 'ALL',
      documentId: '',
      page: 1,
      size: 10,
      sortBy: 'updatedAt',
      direction: 'desc',
      filtersPayload: {},
    });

    expect(params.status).toBe('');
  });

  it('serializes filters payload when provided', () => {
    const params = buildReviewSearchParams({
      statusFilter: 'OPEN',
      documentId: '',
      page: 0,
      size: 10,
      sortBy: 'id',
      direction: 'asc',
      filtersPayload: { branch_code: 'BR001' },
    });

    expect(params.filters).toBe(JSON.stringify({ branch_code: 'BR001' }));
  });
});
