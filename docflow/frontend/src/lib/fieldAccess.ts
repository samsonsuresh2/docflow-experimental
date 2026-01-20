import { UploadFieldDefinition } from './config';
import { normalizeStatus } from './documentStatus';
import { DynamicFormValues } from './dynamicFormValues';

export type FieldAccessResult = {
  isVisible: boolean;
  isEditable: boolean;
  isRequiredNow: boolean;
  isLocked: boolean;
};

type StatusValue = string | null | undefined;

const EDITABLE_STATUSES = new Set(['DRAFT', 'REWORK']);

export function evaluateFieldAccess(
  field: UploadFieldDefinition,
  context: {
    activeRole?: string | null;
    documentStatus?: StatusValue;
    currentValue?: unknown;
    visibleIfPasses?: boolean;
  },
): FieldAccessResult {
  const visibleIfPasses = context.visibleIfPasses ?? true;
  const roleAllowed =
    !field.visibleToRoles || field.visibleToRoles.length === 0
      ? true
      : Boolean(context.activeRole) &&
        field.visibleToRoles.some((role) => roleLocaleEquals(role, context.activeRole ?? ''));
  const isVisible = visibleIfPasses && roleAllowed;

  const lockAfterFilled = Boolean(field.lockAfterFilled);
  const isLocked = lockAfterFilled && isValueFilled(context.currentValue);

  const status = normalizeStatusForAccess(context.documentStatus);
  const editableByStatus = !status || EDITABLE_STATUSES.has(status);
  const roleEditable =
    !field.editableByRoles || field.editableByRoles.length === 0
      ? true
      : Boolean(context.activeRole) &&
        field.editableByRoles.some((role) => roleLocaleEquals(role, context.activeRole ?? ''));
  const isEditable = editableByStatus && roleEditable && !isLocked;

  const requiredStatuses = field.requiredAtStatuses ?? [];
  const statusRequired =
    requiredStatuses.length === 0
      ? true
      : requiredStatuses.some((required) => normalizeStatusForAccess(required) === status);
  const isRequiredNow =
    isVisible &&
    (requiredStatuses.length > 0 ? statusRequired : Boolean(field.required));

  return {
    isVisible,
    isEditable,
    isRequiredNow,
    isLocked,
  };
}

export function evaluateVisibleIf(field: UploadFieldDefinition, values: DynamicFormValues | Record<string, unknown>) {
  if (!field.visibleIf || !field.visibleIf.field) {
    return true;
  }
  const candidate = values?.[field.visibleIf.field];
  if (candidate === undefined || candidate === null) {
    return true;
  }
  if (!field.visibleIf.notIn || field.visibleIf.notIn.length === 0) {
    return true;
  }
  if (Array.isArray(candidate)) {
    return !candidate.some((item) => field.visibleIf?.notIn?.includes(String(item)));
  }
  return !field.visibleIf.notIn.includes(String(candidate));
}

export function isValueFilled(value: unknown): boolean {
  if (value === null || value === undefined) {
    return false;
  }
  if (typeof value === 'string') {
    return value.trim().length > 0;
  }
  if (typeof value === 'boolean') {
    return true;
  }
  if (Array.isArray(value)) {
    return value.length > 0;
  }
  return true;
}

export function buildFieldAccessMap(
  fields: UploadFieldDefinition[],
  values: DynamicFormValues | Record<string, unknown>,
  context: { activeRole?: string | null; documentStatus?: StatusValue },
): Map<string, FieldAccessResult> {
  const access = new Map<string, FieldAccessResult>();
  fields.forEach((field) => {
    const visibleIfPasses = evaluateVisibleIf(field, values);
    const currentValue = values ? values[field.name] : undefined;
    access.set(
      field.name,
      evaluateFieldAccess(field, {
        ...context,
        currentValue,
        visibleIfPasses,
      }),
    );
  });
  return access;
}

function roleLocaleEquals(left: string, right: string): boolean {
  return left.localeCompare(right, undefined, { sensitivity: 'accent', usage: 'search' }) === 0;
}

function normalizeStatusForAccess(status: StatusValue): string | null {
  if (!status) {
    return null;
  }
  const normalized = typeof status === 'string' ? status.trim().toUpperCase() : String(status);
  const mapped = normalizeStatus(normalized);
  if (!mapped) {
    return normalized;
  }
  return mapped.toUpperCase();
}
