export type WorkflowActionKey =
  | 'submit'
  | 'startReview'
  | 'reviewApprove'
  | 'approve'
  | 'reject'
  | 'rework'
  | 'close';

export type WorkflowActionDescriptor = {
  key: WorkflowActionKey;
  label: string;
};

const WORKFLOW_ACTIONS: Record<string, WorkflowActionDescriptor> = {
  SUBMIT: { key: 'submit', label: 'Submit for Review' },
  START_REVIEW: { key: 'startReview', label: 'Start Review' },
  REVIEW_APPROVE: { key: 'reviewApprove', label: 'Approve' },
  APPROVE: { key: 'approve', label: 'Approve' },
  REJECT: { key: 'reject', label: 'Reject' },
  REWORK: { key: 'rework', label: 'Rework' },
  CLOSE: { key: 'close', label: 'Close Document' },
};

export function mapAllowedActions(allowedActions: string[] | null | undefined): WorkflowActionDescriptor[] {
  return (allowedActions ?? [])
    .map((action) => WORKFLOW_ACTIONS[action])
    .filter((action): action is WorkflowActionDescriptor => Boolean(action));
}

export function hasAllowedAction(allowedActions: string[] | null | undefined, actionCode: string): boolean {
  if (!actionCode) {
    return false;
  }
  const normalized = actionCode.trim().toUpperCase();
  return (allowedActions ?? []).some((action) => action.trim().toUpperCase() === normalized);
}
