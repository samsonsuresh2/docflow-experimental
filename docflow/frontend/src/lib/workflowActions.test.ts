import { describe, expect, it } from 'vitest';
import { mapAllowedActions } from './workflowActions';

describe('mapAllowedActions', () => {
  it('maps known action codes to descriptors', () => {
    const result = mapAllowedActions(['SUBMIT', 'REVIEW_APPROVE', 'APPROVE']);
    expect(result).toEqual([
      { key: 'submit', label: 'Submit for Review' },
      { key: 'reviewApprove', label: 'Approve' },
      { key: 'approve', label: 'Approve' },
    ]);
  });

  it('filters unknown action codes', () => {
    const result = mapAllowedActions(['UNKNOWN', 'REJECT']);
    expect(result).toEqual([{ key: 'reject', label: 'Reject' }]);
  });
});
