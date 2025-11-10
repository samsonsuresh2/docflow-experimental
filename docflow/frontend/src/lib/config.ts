import { UserRole } from './user';

export type UploadFieldType =
  | 'text'
  | 'number'
  | 'textarea'
  | 'email'
  | 'password'
  | 'readonly'
  | 'dropdown'
  | 'multiselect'
  | 'radio'
  | 'checkbox'
  | 'checkbox-group'
  | 'date'
  | 'datetime'
  | 'time'
  | 'month';

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
  defaultValue?: unknown;
  rows?: number;
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
  const typeValue =
    typeof obj.type === 'string' ? obj.type.trim().toLowerCase() : 'text';
  const typeAliases: Record<string, UploadFieldType> = {
    select: 'dropdown',
    'select-one': 'dropdown',
    'single-select': 'dropdown',
    'single_select': 'dropdown',
    'drop-down': 'dropdown',
    'drop_down': 'dropdown',
    'multi-select': 'multiselect',
    'multi_select': 'multiselect',
    'checkboxes': 'checkbox-group',
    'checkbox_list': 'checkbox-group',
    'datetime-local': 'datetime',
  };
  const allowedTypes: UploadFieldType[] = [
    'text',
    'number',
    'textarea',
    'email',
    'password',
    'readonly',
    'dropdown',
    'multiselect',
    'radio',
    'checkbox',
    'checkbox-group',
    'date',
    'datetime',
    'time',
    'month',
  ];
  const canonicalType = (typeAliases[typeValue] ?? typeValue) as UploadFieldType;
  const type = allowedTypes.includes(canonicalType) ? canonicalType : 'text';
  const required = Boolean(obj.required);
  const placeholder = typeof obj.placeholder === 'string' ? obj.placeholder : undefined;
  const readOnly = Boolean(obj.readOnly);
  const rows =
    typeof obj.rows === 'number' && Number.isFinite(obj.rows) ? Math.max(1, Math.floor(obj.rows)) : undefined;

  const options = Array.isArray(obj.options)
    ? obj.options
        .map((option) => {
          if (option === null || option === undefined) {
            return null;
          }
          if (typeof option === 'string' || typeof option === 'number' || typeof option === 'boolean') {
            const value = String(option);
            return { label: value, value };
          }
          if (typeof option === 'object') {
            const optionObj = option as Record<string, unknown>;
            const rawValue = optionObj.value ?? optionObj.id ?? optionObj.key ?? optionObj.name;
            if (rawValue === undefined) {
              return null;
            }
            const value = String(rawValue);
            const labelValue = optionObj.label ?? optionObj.title ?? optionObj.name ?? value;
            return { label: String(labelValue), value };
          }
          return null;
        })
        .filter((option): option is FieldOption => Boolean(option))
    : undefined;

  const roles = Array.isArray(obj.roles)
    ? (obj.roles
        .map((role) => (typeof role === 'string' ? role.toUpperCase() : null))
        .filter((role): role is UserRole => role === 'ADMIN' || role === 'MAKER' || role === 'REVIEWER' || role === 'CHECKER'))
    : undefined;

  const defaultValue = obj.defaultValue;

  return {
    name,
    label,
    type,
    required,
    placeholder,
    readOnly,
    options,
    roles,
    defaultValue,
    rows,
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
