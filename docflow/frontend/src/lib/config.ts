import { UserRole } from './user';

export type UploadFieldType = 'text' | 'number' | 'date' | 'select' | 'textarea';

export interface FieldOption {
  label: string;
  value: string;
}

export interface UploadFieldDefinition {
  name: string;
  label: string;
  type: UploadFieldType;
  required?: boolean;
  placeholder?: string;
  options?: FieldOption[];
  roles?: UserRole[];
  readOnly?: boolean;
}

export function parseUploadFieldConfig(raw: string | null | undefined): UploadFieldDefinition[] {
  if (!raw) {
    return [];
  }

  try {
    const parsed = JSON.parse(raw) as unknown;
    const fieldArray = Array.isArray(parsed)
      ? parsed
      : typeof parsed === 'object' && parsed !== null && Array.isArray((parsed as { fields?: unknown }).fields)
      ? (parsed as { fields: unknown[] }).fields
      : [];

    return fieldArray
      .map((candidate) => normaliseField(candidate))
      .filter((field): field is UploadFieldDefinition => Boolean(field && field.name));
  } catch {
    return [];
  }
}

function normaliseField(candidate: unknown): UploadFieldDefinition | null {
  if (!candidate || typeof candidate !== 'object') {
    return null;
  }

  const obj = candidate as Record<string, unknown>;

  const name = typeof obj.name === 'string' ? obj.name : '';
  if (!name) {
    return null;
  }

  const label = typeof obj.label === 'string' ? obj.label : name;
  const typeValue = typeof obj.type === 'string' ? obj.type.toLowerCase() : 'text';
  const allowedTypes: UploadFieldType[] = ['text', 'number', 'date', 'select', 'textarea'];
  const type = (allowedTypes.includes(typeValue as UploadFieldType) ? typeValue : 'text') as UploadFieldType;
  const required = Boolean(obj.required);
  const placeholder = typeof obj.placeholder === 'string' ? obj.placeholder : undefined;
  const readOnly = Boolean(obj.readOnly);

  const options = Array.isArray(obj.options)
    ? obj.options
        .map((option) => {
          if (!option || typeof option !== 'object') {
            return null;
          }
          const optionObj = option as Record<string, unknown>;
          if (optionObj.value === undefined) {
            return null;
          }
          const value = String(optionObj.value);
          const labelValue = optionObj.label !== undefined ? String(optionObj.label) : value;
          return { label: labelValue, value };
        })
        .filter((option): option is FieldOption => Boolean(option))
    : undefined;

  const roles = Array.isArray(obj.roles)
    ? (obj.roles
        .map((role) => (typeof role === 'string' ? role.toUpperCase() : null))
        .filter((role): role is UserRole => role === 'ADMIN' || role === 'MAKER' || role === 'REVIEWER' || role === 'CHECKER'))
    : undefined;

  return {
    name,
    label,
    type,
    required,
    placeholder,
    readOnly,
    options,
    roles,
  };
}

export function fieldsForRole(fields: UploadFieldDefinition[], role: UserRole | null | undefined) {
  if (!role) {
    return fields;
  }
  return fields.filter((field) => {
    if (!field.roles || field.roles.length === 0) {
      return true;
    }
    return field.roles.includes(role);
  });
}
