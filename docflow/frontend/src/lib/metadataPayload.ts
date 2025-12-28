import { UploadFieldDefinition } from './config';
import { FieldAccessResult } from './fieldAccess';
import { DynamicFormValues, isDynamicFormValueEmpty } from './dynamicFormValues';

export function buildMetadataPayload(
  fields: UploadFieldDefinition[],
  values: DynamicFormValues,
  existingMetadata: Record<string, unknown> = {},
  accessMap?: Map<string, FieldAccessResult>,
): Record<string, unknown> {
  const metadata: Record<string, unknown> = { ...existingMetadata };

  fields.forEach((field) => {
    const access = accessMap?.get(field.name);
    const isVisible = access?.isVisible ?? true;
    const isEditable = access?.isEditable ?? true;
    if (!isVisible || !isEditable) {
      return;
    }

    const rawValue = values[field.name];
    if (rawValue === undefined) {
      return;
    }

    switch (field.type) {
      case 'number': {
        if (typeof rawValue === 'string') {
          const parsed = Number(rawValue);
          metadata[field.name] = Number.isNaN(parsed) ? rawValue : parsed;
        } else {
          metadata[field.name] = rawValue;
        }
        break;
      }
      case 'checkbox': {
        metadata[field.name] = Boolean(rawValue);
        break;
      }
      case 'multiselect':
      case 'checkbox-group': {
        if (Array.isArray(rawValue) && rawValue.length > 0) {
          metadata[field.name] = rawValue;
        } else if (typeof rawValue === 'string' && rawValue) {
          metadata[field.name] = [rawValue];
        } else {
          delete metadata[field.name];
        }
        break;
      }
      default: {
        if (typeof rawValue !== 'boolean' && isDynamicFormValueEmpty(rawValue)) {
          delete metadata[field.name];
        } else {
          metadata[field.name] = rawValue;
        }
      }
    }
  });

  return metadata;
}
