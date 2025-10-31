import { type ChangeEvent, useEffect, useMemo, useState } from 'react';
import { UploadFieldDefinition } from '../lib/config';

type Props = {
  fields: UploadFieldDefinition[];
  initialValues?: Record<string, string>;
  onSubmit?: (values: Record<string, string>) => void;
  onChange?: (values: Record<string, string>) => void;
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
  const [values, setValues] = useState<Record<string, string>>(() => initialValues ?? {});

  useEffect(() => {
    if (initialValues) {
      setValues(initialValues);
    }
  }, [initialValues]);

  const effectiveFields = useMemo(
    () =>
      fields.map((field) => ({
        ...field,
        type: field.type ?? 'text',
      })),
    [fields],
  );

  const handleChange = (name: string, value: string) => {
    if (disabled) {
      return;
    }
    setValues((prev) => {
      const next = { ...prev, [name]: value };
      onChange?.(next);
      return next;
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
          {renderInput(field, values[field.name] ?? '', (value) => handleChange(field.name, value), disabled)}
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
  value: string,
  onChange: (value: string) => void,
  formDisabled: boolean,
) {
  const commonProps = {
    disabled: formDisabled || field.readOnly,
    required: field.required,
    placeholder: field.placeholder,
    value,
    onChange: (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
      onChange(event.target.value),
    className:
      'mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 disabled:cursor-not-allowed disabled:bg-slate-100 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40 dark:disabled:bg-slate-800',
  };

  switch (field.type) {
    case 'number':
      return <input type="number" {...commonProps} />;
    case 'date':
      return <input type="date" {...commonProps} />;
    case 'select':
      return (
        <select {...commonProps}>
          <option value="">Select…</option>
          {(field.options ?? []).map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      );
    case 'textarea':
      return <textarea rows={3} {...commonProps} />;
    default:
      return <input type="text" {...commonProps} />;
  }
}
