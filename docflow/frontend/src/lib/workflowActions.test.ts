import { describe, expect, it } from 'vitest';
import { mapAllowedActions } from './workflowActions';

describe('mapAllowedActions', () => {
  it('maps known action codes to descriptors', () => {
    const result = mapAllowedActions(['SUBMIT', 'APPROVE']);
    expect(result).toEqual([
      { key: 'submit', label: 'Submit for Review' },
      { key: 'approve', label: 'Approve' },
    ]);
  });

  it('filters unknown action codes', () => {
    const result = mapAllowedActions(['UNKNOWN', 'REJECT']);
    expect(result).toEqual([{ key: 'reject', label: 'Reject' }]);
  });
});
