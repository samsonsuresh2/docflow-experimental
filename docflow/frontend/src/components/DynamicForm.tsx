import { useEffect, useMemo, useState, type ChangeEvent } from 'react';
import { UploadFieldDefinition } from '../lib/config';
import {
  buildDefaultValues,
  DynamicFormValue,
  DynamicFormValues,
  getDefaultValueForField,
  normaliseValueForField,
} from '../lib/dynamicFormValues';

type Props = {
  fields: UploadFieldDefinition[];
  initialValues?: Record<string, unknown>;
  onSubmit?: (values: DynamicFormValues) => void;
  onChange?: (values: DynamicFormValues) => void;
  submitLabel?: string | null;
  disabled?: boolean;
};

export default function DynamicForm({
  fields,
  initialValues,
  onSubmit,
  onChange,
  submitLabel = 'Submit',
  disabled = false,
}: Props) {
  const effectiveFields = useMemo(
    () =>
      fields.map((field) => ({
        ...field,
        type: field.type ?? 'text',
      })),
    [fields],
  );

  const fieldMap = useMemo(() => {
    const map = new Map<string, UploadFieldDefinition>();
    effectiveFields.forEach((field) => {
      map.set(field.name, field);
    });
    return map;
  }, [effectiveFields]);

  const defaultValues = useMemo(() => buildDefaultValues(effectiveFields), [effectiveFields]);

  const normalisedInitialValues = useMemo(() => {
    if (!initialValues) {
      return {} as DynamicFormValues;
    }
    const result: DynamicFormValues = {};
    Object.entries(initialValues).forEach(([name, rawValue]) => {
      const field = fieldMap.get(name);
      if (field) {
        result[name] = normaliseValueForField(field, rawValue);
      }
    });
    return result;
  }, [initialValues, fieldMap]);

  const combinedInitialValues = useMemo(() => {
    const next: DynamicFormValues = { ...defaultValues };
    Object.entries(normalisedInitialValues).forEach(([key, value]) => {
      next[key] = value;
    });
    return next;
  }, [defaultValues, normalisedInitialValues]);

  const [values, setValues] = useState<DynamicFormValues>(() => combinedInitialValues);

  useEffect(() => {
    setValues((prev) => {
      if (recordsAreEqual(prev, combinedInitialValues)) {
        return prev;
      }
      return combinedInitialValues;
    });
  }, [combinedInitialValues]);

  useEffect(() => {
    onChange?.(values);
  }, [values, onChange]);

  const handleChange = (name: string, value: DynamicFormValue) => {
    if (disabled) {
      return;
    }
    const field = fieldMap.get(name);
    const normalisedValue = field ? normaliseValueForField(field, value) : value;
    setValues((prev) => {
      const current = prev[name];
      if (valuesAreEqual(current, normalisedValue)) {
        return prev;
      }
      return { ...prev, [name]: normalisedValue };
    });
  };

  const formContent = (
    <div className="space-y-4">
      {effectiveFields.map((field) => (
        <label key={field.name} className="block text-sm">
          <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
            {field.label}
            {field.required ? <span className="ml-1 text-red-500">*</span> : null}
          </span>
          {renderInput(
            field,
            values[field.name] ?? getDefaultValueForField(field),
            (value) => handleChange(field.name, value),
            disabled,
          )}
        </label>
      ))}
      {onSubmit && submitLabel !== null ? (
        <button
          type="submit"
          disabled={disabled}
          className="inline-flex items-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300 dark:bg-blue-500 dark:hover:bg-blue-400 dark:disabled:bg-blue-400/50"
        >
          {submitLabel ?? 'Submit'}
        </button>
      ) : null}
    </div>
  );

  if (onSubmit) {
    return (
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          if (!disabled) {
            onSubmit(values);
          }
        }}
      >
        {formContent}
      </form>
    );
  }

  return formContent;
}

function renderInput(
  field: UploadFieldDefinition,
  value: DynamicFormValue,
  onChange: (value: DynamicFormValue) => void,
  formDisabled: boolean,
) {
  const isExplicitReadOnly = field.type === 'readonly';
  const isDisabled = formDisabled || (!isExplicitReadOnly && Boolean(field.readOnly));
  const isReadOnly = isExplicitReadOnly || Boolean(field.readOnly);
  const baseClassName =
    'mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 disabled:cursor-not-allowed disabled:bg-slate-100 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40 dark:disabled:bg-slate-800';

  const handleStringChange = (
    event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>,
  ) => {
    onChange(event.target.value);
  };

  switch (field.type) {
    case 'textarea': {
      return (
        <textarea
          className={baseClassName}
          disabled={isDisabled}
          required={field.required}
          placeholder={field.placeholder}
          rows={field.rows ?? 3}
          value={typeof value === 'string' ? value : ''}
          onChange={handleStringChange}
        />
      );
    }
    case 'number':
    case 'text':
    case 'email':
    case 'password':
    case 'date':
    case 'time':
    case 'month':
    case 'datetime':
    case 'readonly': {
      const typeMap: Record<string, string> = {
        number: 'number',
        text: 'text',
        email: 'email',
        password: 'password',
        date: 'date',
        time: 'time',
        month: 'month',
        datetime: 'datetime-local',
        readonly: 'text',
      };
      return (
        <input
          type={typeMap[field.type] ?? 'text'}
          className={baseClassName}
          disabled={isDisabled}
          required={field.required}
          placeholder={field.placeholder}
          value={typeof value === 'string' ? value : ''}
          onChange={handleStringChange}
          readOnly={isReadOnly}
        />
      );
    }
    case 'dropdown': {
      const options = field.options ?? [];
      return (
        <select
          className={baseClassName}
          disabled={isDisabled}
          required={field.required}
          value={typeof value === 'string' ? value : ''}
          onChange={handleStringChange}
        >
          <option value="">{field.placeholder ?? 'Select…'}</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      );
    }
    case 'multiselect': {
      const options = field.options ?? [];
      return (
        <select
          multiple
          className={baseClassName}
          disabled={isDisabled}
          required={field.required}
          value={Array.isArray(value) ? value : []}
          onChange={(event) => {
            const selected = Array.from(event.target.selectedOptions).map((option) => option.value);
            onChange(selected);
          }}
        >
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      );
    }
    case 'radio': {
      const options = field.options ?? [];
      const selected = typeof value === 'string' ? value : '';
      return (
        <div className="mt-2 space-y-2">
          {options.map((option, index) => (
            <label
              key={option.value}
              className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-200"
            >
              <input
                type="radio"
                name={field.name}
                value={option.value}
                className="h-4 w-4 border-slate-300 text-blue-600 focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-900"
                checked={selected === option.value}
                disabled={isDisabled}
                required={Boolean(field.required) && index === 0}
                onChange={(event) => {
                  if (event.target.checked) {
                    onChange(option.value);
                  }
                }}
              />
              <span>{option.label}</span>
            </label>
          ))}
        </div>
      );
    }
    case 'checkbox': {
      return (
        <input
          type="checkbox"
          className="mt-2 h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 disabled:cursor-not-allowed dark:border-slate-600 dark:bg-slate-900"
          checked={Boolean(value)}
          disabled={isDisabled}
          required={field.required}
          onChange={(event) => onChange(event.target.checked)}
        />
      );
    }
    case 'checkbox-group': {
      const options = field.options ?? [];
      const selectedValues = new Set(Array.isArray(value) ? value : []);
      return (
        <div className="mt-2 space-y-2">
          {options.map((option) => (
            <label
              key={option.value}
              className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-200"
            >
              <input
                type="checkbox"
                className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 disabled:cursor-not-allowed dark:border-slate-600 dark:bg-slate-900"
                checked={selectedValues.has(option.value)}
                disabled={isDisabled}
                onChange={(event) => {
                  const next = new Set(selectedValues);
                  if (event.target.checked) {
                    next.add(option.value);
                  } else {
                    next.delete(option.value);
                  }
                  onChange(Array.from(next));
                }}
              />
              <span>{option.label}</span>
            </label>
          ))}
        </div>
      );
    }
    default: {
      return (
        <input
          type="text"
          className={baseClassName}
          disabled={isDisabled}
          required={field.required}
          placeholder={field.placeholder}
          value={typeof value === 'string' ? value : ''}
          onChange={handleStringChange}
          readOnly={isReadOnly}
        />
      );
    }
  }
}

function valuesAreEqual(current: DynamicFormValue | undefined, next: DynamicFormValue | undefined) {
  if (Array.isArray(current) || Array.isArray(next)) {
    if (!Array.isArray(current) || !Array.isArray(next)) {
      return false;
    }
    if (current.length !== next.length) {
      return false;
    }
    return current.every((item, index) => item === next[index]);
  }
  return current === next;
}

function recordsAreEqual(a: DynamicFormValues, b: DynamicFormValues) {
  const aKeys = Object.keys(a);
  const bKeys = Object.keys(b);
  if (aKeys.length !== bKeys.length) {
    return false;
  }
  for (const key of aKeys) {
    if (!valuesAreEqual(a[key], b[key])) {
      return false;
    }
  }
  return true;
}
