import type { ChangeEvent } from 'react';
import {
  REPORT_MAIL_FIELDS,
  type ReportMailAttachmentFormat,
  type ReportMailConfig,
  type ReportMailFieldName,
} from '../types/reports';

type ReportMailFieldConfig = ReportMailConfig['fields'][ReportMailFieldName];

type Props = {
  value: ReportMailConfig;
  onChange: (value: ReportMailConfig) => void;
  showEnabledToggle?: boolean;
};

const FIELD_LABELS: Record<ReportMailFieldName, string> = {
  to: 'To',
  cc: 'CC',
  subject: 'Subject',
  body: 'Body',
  disclaimer: 'Disclaimer',
};

const DEFAULT_PLACEHOLDERS: Record<ReportMailFieldName, string> = {
  to: 'recipient@company.internal, team@company.internal',
  cc: 'optional@company.internal',
  subject: 'Monthly report for ${reportName}',
  body: 'Hello,\n\nPlease find the report below.',
  disclaimer: 'Optional disclaimer text.',
};

const MANDATORY_PLACEHOLDERS: Record<ReportMailFieldName, string> = {
  to: 'always@company.internal',
  cc: 'audit@company.internal',
  subject: '[Mandatory Prefix]',
  body: 'Mandatory intro text.',
  disclaimer: 'Mandatory disclaimer text.',
};

export default function ReportMailConfigEditor({ value, onChange, showEnabledToggle = true }: Props) {
  const updateField = (fieldName: ReportMailFieldName, patch: Partial<ReportMailFieldConfig>) => {
    onChange({
      ...value,
      fields: {
        ...value.fields,
        [fieldName]: {
          ...value.fields[fieldName],
          ...patch,
        },
      },
    });
  };

  const handleEnabledChange = (event: ChangeEvent<HTMLInputElement>) => {
    onChange({
      ...value,
      enabled: event.target.checked,
    });
  };

  const handleAttachmentFormatChange = (event: ChangeEvent<HTMLSelectElement>) => {
    onChange({
      ...value,
      attachmentFormat: event.target.value as ReportMailAttachmentFormat,
    });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-950/40">
        {showEnabledToggle ? (
          <label className="inline-flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
            <input
              type="checkbox"
              checked={value.enabled}
              onChange={handleEnabledChange}
              className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-900"
            />
            Enable report mail
          </label>
        ) : (
          <div>
            <p className="text-sm font-semibold text-slate-700 dark:text-slate-200">Email output settings</p>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              These values are stored in the report template JSON and applied when email output is enabled.
            </p>
          </div>
        )}
        <div className="flex flex-wrap items-center gap-3">
          <label className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
            Mail mode
            <select
              value={value.mode}
              onChange={(event) => onChange({ ...value, mode: event.target.value as ReportMailConfig['mode'] })}
              className="rounded border border-slate-300 px-2 py-1 text-xs transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
            >
              <option value="INLINE_ONLY">INLINE_ONLY</option>
              <option value="ATTACHMENT_ONLY">ATTACHMENT_ONLY</option>
              <option value="INLINE_OR_ATTACHMENT">INLINE_OR_ATTACHMENT</option>
            </select>
          </label>
          <label className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
            Attachment format
            <select
              value={value.attachmentFormat}
              onChange={handleAttachmentFormatChange}
              className="rounded border border-slate-300 px-2 py-1 text-xs transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
            >
              <option value="CSV">CSV</option>
              <option value="EXCEL">EXCEL</option>
            </select>
          </label>
        </div>
      </div>

      <div className="space-y-3">
        {REPORT_MAIL_FIELDS.map((fieldName) => {
          const field = value.fields[fieldName];
          const inputBaseClass =
            'mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 disabled:cursor-not-allowed disabled:bg-slate-100 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40 dark:disabled:bg-slate-800';
          const isTextArea = fieldName === 'body' || fieldName === 'disclaimer';
          return (
            <div key={fieldName} className="rounded border border-slate-200 p-4 transition-colors dark:border-slate-700">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-100">{FIELD_LABELS[fieldName]}</h3>
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    Mandatory values are always enforced. Defaults are suggestions unless the field is read-only.
                  </p>
                </div>
                <label className="inline-flex items-center gap-1 rounded border border-slate-200 px-2 py-1 text-xs font-semibold uppercase tracking-wide text-slate-600 dark:border-slate-700 dark:text-slate-300">
                  <input
                    type="checkbox"
                    checked={field.editable}
                    onChange={(event) => updateField(fieldName, { editable: event.target.checked })}
                    className="h-3 w-3 rounded border-slate-300 text-blue-600 focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-900"
                  />
                  Editable
                </label>
              </div>

              <div className="mt-3 grid gap-3 lg:grid-cols-2">
                <label className="block rounded-xl border border-blue-200 bg-blue-50/70 p-3 dark:border-blue-400/20 dark:bg-blue-500/10">
                  <span className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-200">Default runtime value</span>
                  <p className="mt-1 text-[11px] text-blue-700/80 dark:text-blue-200/80">
                    Users see this first. They can change it only when the field stays editable.
                  </p>
                  {isTextArea ? (
                    <textarea
                      className={inputBaseClass}
                      rows={fieldName === 'body' ? 4 : 3}
                      value={field.default}
                      onChange={(event) => updateField(fieldName, { default: event.target.value })}
                      placeholder={DEFAULT_PLACEHOLDERS[fieldName]}
                    />
                  ) : (
                    <input
                      type="text"
                      className={inputBaseClass}
                      value={field.default}
                      onChange={(event) => updateField(fieldName, { default: event.target.value })}
                      placeholder={DEFAULT_PLACEHOLDERS[fieldName]}
                    />
                  )}
                </label>

                <label className="block rounded-xl border border-slate-200 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-950/60">
                  <span className="text-xs font-semibold uppercase tracking-wide text-slate-700 dark:text-slate-200">Mandatory backend value</span>
                  <p className="mt-1 text-[11px] text-slate-500 dark:text-slate-400">
                    These values are always enforced and shown as locked content in the compose dialog.
                  </p>
                  {isTextArea ? (
                    <textarea
                      className={inputBaseClass}
                      rows={fieldName === 'body' ? 3 : 2}
                      value={field.mandatory}
                      onChange={(event) => updateField(fieldName, { mandatory: event.target.value })}
                      placeholder={MANDATORY_PLACEHOLDERS[fieldName]}
                    />
                  ) : (
                    <input
                      type="text"
                      className={inputBaseClass}
                      value={field.mandatory}
                      onChange={(event) => updateField(fieldName, { mandatory: event.target.value })}
                      placeholder={MANDATORY_PLACEHOLDERS[fieldName]}
                    />
                  )}
                </label>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
