import { UploadFieldDefinition, UploadFieldType } from './config';

export type DynamicFormValue = string | string[] | boolean;
export type DynamicFormValues = Record<string, DynamicFormValue>;

const truthyStrings = new Set(['true', '1', 'yes', 'y', 'on']);
const falsyStrings = new Set(['false', '0', 'no', 'n', 'off']);

function baseDefaultForType(type: UploadFieldType): DynamicFormValue {
  switch (type) {
    case 'checkbox':
      return false;
    case 'multiselect':
    case 'checkbox-group':
      return [];
    default:
      return '';
  }
}

export function getDefaultValueForField(field: UploadFieldDefinition): DynamicFormValue {
  if (field.defaultValue !== undefined) {
    return normaliseValueForField(field, field.defaultValue);
  }
  return baseDefaultForType(field.type);
}

export function buildDefaultValues(fields: UploadFieldDefinition[]): DynamicFormValues {
  const values: DynamicFormValues = {};
  fields.forEach((field) => {
    values[field.name] = getDefaultValueForField(field);
  });
  return values;
}

export function normaliseValueForField(
  field: UploadFieldDefinition,
  value: unknown,
): DynamicFormValue {
  if (value === undefined || value === null) {
    return baseDefaultForType(field.type);
  }

  switch (field.type) {
    case 'checkbox': {
      if (typeof value === 'boolean') {
        return value;
      }
      if (typeof value === 'number') {
        return value !== 0;
      }
      if (Array.isArray(value)) {
        return value.length > 0;
      }
      const normalised = String(value).trim().toLowerCase();
      if (truthyStrings.has(normalised)) {
        return true;
      }
      if (falsyStrings.has(normalised)) {
        return false;
      }
      return normalised.length > 0;
    }
    case 'multiselect':
    case 'checkbox-group': {
      if (Array.isArray(value)) {
        return value.map((item) => String(item));
      }
      const stringValue = String(value);
      return stringValue ? [stringValue] : [];
    }
    default: {
      if (Array.isArray(value)) {
        return value.length > 0 ? String(value[0]) : '';
      }
      return String(value);
    }
  }
}

export function isDynamicFormValueEmpty(value: DynamicFormValue): boolean {
  if (Array.isArray(value)) {
    return value.length === 0;
  }
  if (typeof value === 'boolean') {
    return false;
  }
  return value.trim().length === 0;
}

export function normaliseValuesForFields(
  fields: UploadFieldDefinition[],
  values: Record<string, unknown>,
): DynamicFormValues {
  const fieldMap = new Map<string, UploadFieldDefinition>();
  fields.forEach((field) => {
    fieldMap.set(field.name, field);
  });

  const normalised: DynamicFormValues = {};
  Object.entries(values).forEach(([key, rawValue]) => {
    const field = fieldMap.get(key);
    if (field) {
      normalised[key] = normaliseValueForField(field, rawValue);
    } else if (rawValue !== undefined && rawValue !== null) {
      if (typeof rawValue === 'boolean') {
        normalised[key] = rawValue;
      } else if (Array.isArray(rawValue)) {
        normalised[key] = rawValue.map((item) => String(item));
      } else {
        normalised[key] = String(rawValue);
      }
    }
  });

  return normalised;
}
