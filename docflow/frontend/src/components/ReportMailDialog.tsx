import { useEffect, useMemo, useState } from 'react';
import { joinEmailList, normalizeReportMailConfig, splitEmailList } from '../lib/reportMail';
import type {
  ReportMailApiConfig,
  ReportMailConfig,
  ReportMailDeliveryMode,
  ReportMailRuntimeValues,
  ReportMailSendDraft,
} from '../types/reports';

type Props = {
  isOpen: boolean;
  onClose: () => void;
  templateId: number;
  templateName: string;
  mailConfig: ReportMailApiConfig | ReportMailConfig | null | undefined;
  filterSummary: string;
  onSend: (request: ReportMailSendDraft) => Promise<void>;
};

type RecipientFieldName = 'to' | 'cc';
type TextFieldName = 'subject' | 'body' | 'disclaimer';

const RECIPIENT_FIELDS: RecipientFieldName[] = ['to', 'cc'];
const TEXT_FIELDS: TextFieldName[] = ['subject', 'body', 'disclaimer'];

const FIELD_LABELS: Record<keyof ReportMailRuntimeValues, string> = {
  to: 'To',
  cc: 'Cc',
  subject: 'Subject',
  body: 'Body',
  disclaimer: 'Disclaimer',
};

function splitMultiline(value: string): string[] {
  return value
    .split('\n')
    .map((part) => part.trim())
    .filter(Boolean);
}

function formatMandatoryHint(values: string[]): string {
  if (values.length === 0) {
    return '';
  }
  return `Mandatory: ${values.join(', ')}`;
}

export default function ReportMailDialog({
  isOpen,
  onClose,
  templateId,
  templateName,
  mailConfig,
  filterSummary,
  onSend,
}: Props) {
  const normalizedConfig = useMemo(() => normalizeReportMailConfig(mailConfig), [mailConfig]);
  const mandatoryRecipients = useMemo(
    () => ({
      to: splitEmailList(normalizedConfig.fields.to.mandatory),
      cc: splitEmailList(normalizedConfig.fields.cc.mandatory),
    }),
    [normalizedConfig.fields.cc.mandatory, normalizedConfig.fields.to.mandatory],
  );
  const mandatoryText = useMemo(
    () => ({
      subject: normalizedConfig.fields.subject.mandatory.trim(),
      body: normalizedConfig.fields.body.mandatory,
      disclaimer: normalizedConfig.fields.disclaimer.mandatory,
    }),
    [
      normalizedConfig.fields.body.mandatory,
      normalizedConfig.fields.disclaimer.mandatory,
      normalizedConfig.fields.subject.mandatory,
    ],
  );

  const [values, setValues] = useState<ReportMailRuntimeValues>(() => ({
    to: normalizedConfig.fields.to.default,
    cc: normalizedConfig.fields.cc.default,
    subject: normalizedConfig.fields.subject.default,
    body: normalizedConfig.fields.body.default,
    disclaimer: normalizedConfig.fields.disclaimer.default,
  }));
  const [deliveryMode, setDeliveryMode] = useState<ReportMailDeliveryMode>(
    normalizedConfig.mode === 'ATTACHMENT_ONLY' ? 'ATTACHMENT' : 'INLINE',
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    setValues({
      to: normalizedConfig.fields.to.default,
      cc: normalizedConfig.fields.cc.default,
      subject: normalizedConfig.fields.subject.default,
      body: normalizedConfig.fields.body.default,
      disclaimer: normalizedConfig.fields.disclaimer.default,
    });
    setDeliveryMode(normalizedConfig.mode === 'ATTACHMENT_ONLY' ? 'ATTACHMENT' : 'INLINE');
    setSubmitting(false);
    setError(null);
  }, [isOpen, normalizedConfig]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    const handler = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [isOpen, onClose]);

  if (!isOpen) {
    return null;
  }

  const deliveryOptionsVisible = normalizedConfig.mode === 'INLINE_OR_ATTACHMENT';
  const footerNotes: string[] = [];

  RECIPIENT_FIELDS.forEach((fieldName) => {
    const hint = formatMandatoryHint(mandatoryRecipients[fieldName]);
    if (hint) {
      footerNotes.push(`${FIELD_LABELS[fieldName]}: ${hint}`);
    }
  });

  TEXT_FIELDS.forEach((fieldName) => {
    const content = mandatoryText[fieldName].trim();
    if (content) {
      footerNotes.push(`${FIELD_LABELS[fieldName]} mandatory content is enforced by the backend.`);
    }
  });

  const handleTextChange = (field: keyof ReportMailRuntimeValues, nextValue: string) => {
    setValues((prev) => ({ ...prev, [field]: nextValue }));
  };

  const handleSubmit = async () => {
    const nextValues: ReportMailRuntimeValues = {
      to: joinEmailList(splitEmailList(values.to)),
      cc: joinEmailList(splitEmailList(values.cc)),
      subject: values.subject.trim(),
      body: values.body,
      disclaimer: values.disclaimer,
    };

    setSubmitting(true);
    setError(null);
    try {
      await onSend({
        templateId,
        filterSummary,
        deliveryMode: normalizedConfig.mode === 'ATTACHMENT_ONLY' ? 'ATTACHMENT' : deliveryMode,
        fields: nextValues,
      });
      onClose();
    } catch (sendError) {
      setError(normaliseDialogError(sendError));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/55 p-4 backdrop-blur-sm" onClick={onClose} role="presentation">
      <div
        className="relative flex max-h-[90vh] w-full max-w-5xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-900"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Report mail"
      >
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3 dark:border-slate-700">
          <div className="min-w-0">
            <p className="truncate text-base font-semibold text-slate-900 dark:text-slate-100">{templateName || 'New message'}</p>
          </div>
          <button
            type="button"
            className="inline-flex h-8 w-8 items-center justify-center rounded-full text-slate-500 transition hover:bg-slate-100 hover:text-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
            onClick={onClose}
            aria-label="Close report mail"
          >
            <span aria-hidden="true">x</span>
          </button>
        </div>

        <div className="flex-1 overflow-auto">
          <div className="divide-y divide-slate-200 dark:divide-slate-700">
            {RECIPIENT_FIELDS.map((fieldName) => {
              const fieldConfig = normalizedConfig.fields[fieldName];
              const mandatoryHint = formatMandatoryHint(mandatoryRecipients[fieldName]);
              return (
                <div key={fieldName} className="grid items-center gap-3 px-5 py-3 md:grid-cols-[72px_minmax(0,1fr)]">
                  <label
                    htmlFor={`mail-${fieldName}`}
                    className="text-sm font-medium text-slate-700 dark:text-slate-200"
                  >
                    {FIELD_LABELS[fieldName]}
                  </label>
                  <div className="min-w-0">
                    <input
                      id={`mail-${fieldName}`}
                      type="text"
                      value={values[fieldName]}
                      onChange={(event) => handleTextChange(fieldName, event.target.value)}
                      readOnly={!fieldConfig.editable}
                      disabled={!fieldConfig.editable}
                      placeholder={fieldName === 'to' ? 'Recipients' : 'Optional recipients'}
                      className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 transition-colors focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200 disabled:cursor-not-allowed disabled:bg-slate-100 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/30 dark:disabled:bg-slate-800"
                    />
                    {mandatoryHint ? (
                      <p className="mt-1 text-xs text-red-600 dark:text-red-400">{mandatoryHint}</p>
                    ) : null}
                  </div>
                </div>
              );
            })}

            <div className="grid items-center gap-3 px-5 py-3 md:grid-cols-[72px_minmax(0,1fr)]">
              <label htmlFor="mail-subject" className="text-sm font-medium text-slate-700 dark:text-slate-200">
                Subject
              </label>
              <div className="min-w-0">
                <input
                  id="mail-subject"
                  type="text"
                  value={values.subject}
                  onChange={(event) => handleTextChange('subject', event.target.value)}
                  readOnly={!normalizedConfig.fields.subject.editable}
                  disabled={!normalizedConfig.fields.subject.editable}
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 transition-colors focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200 disabled:cursor-not-allowed disabled:bg-slate-100 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/30 dark:disabled:bg-slate-800"
                />
                {mandatoryText.subject ? (
                  <p className="mt-1 text-xs text-red-600 dark:text-red-400">Mandatory: {mandatoryText.subject}</p>
                ) : null}
              </div>
            </div>

            <div className="px-5 py-3">
              <textarea
                id="mail-body"
                value={values.body}
                onChange={(event) => handleTextChange('body', event.target.value)}
                readOnly={!normalizedConfig.fields.body.editable}
                disabled={!normalizedConfig.fields.body.editable}
                rows={11}
                placeholder="Write your message"
                className="w-full resize-none rounded-lg border border-slate-300 bg-white px-3 py-3 text-sm text-slate-900 transition-colors focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200 disabled:cursor-not-allowed disabled:bg-slate-100 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/30 dark:disabled:bg-slate-800"
              />
              {mandatoryText.body.trim() ? (
                <div className="mt-2 space-y-1 text-xs text-red-600 dark:text-red-400">
                  {splitMultiline(mandatoryText.body).map((line, index) => (
                    <p key={`body-mandatory-${index}`}>Mandatory: {line}</p>
                  ))}
                </div>
              ) : null}
            </div>

            <div className="grid gap-3 px-5 py-3 md:grid-cols-[72px_minmax(0,1fr)]">
              <label htmlFor="mail-disclaimer" className="pt-2 text-sm font-medium text-slate-700 dark:text-slate-200">
                Disclaimer
              </label>
              <div className="min-w-0">
                <textarea
                  id="mail-disclaimer"
                  value={values.disclaimer}
                  onChange={(event) => handleTextChange('disclaimer', event.target.value)}
                  readOnly={!normalizedConfig.fields.disclaimer.editable}
                  disabled={!normalizedConfig.fields.disclaimer.editable}
                  rows={3}
                  className="w-full resize-none rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 transition-colors focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200 disabled:cursor-not-allowed disabled:bg-slate-100 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/30 dark:disabled:bg-slate-800"
                />
                {mandatoryText.disclaimer.trim() ? (
                  <div className="mt-1 space-y-1 text-xs text-red-600 dark:text-red-400">
                    {splitMultiline(mandatoryText.disclaimer).map((line, index) => (
                      <p key={`disclaimer-mandatory-${index}`}>Mandatory: {line}</p>
                    ))}
                  </div>
                ) : null}
              </div>
            </div>
          </div>
        </div>

        <div className="border-t border-slate-200 bg-slate-50 px-5 py-3 dark:border-slate-700 dark:bg-slate-950/60">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex min-w-0 flex-col gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Delivery mode</span>
                {deliveryOptionsVisible ? (
                  <div className="inline-flex rounded-lg border border-slate-300 bg-white p-1 dark:border-slate-600 dark:bg-slate-900">
                    <button
                      type="button"
                      className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${
                        deliveryMode === 'INLINE'
                          ? 'bg-blue-600 text-white shadow-sm'
                          : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
                      }`}
                      onClick={() => setDeliveryMode('INLINE')}
                    >
                      Inline
                    </button>
                    <button
                      type="button"
                      className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${
                        deliveryMode === 'ATTACHMENT'
                          ? 'bg-blue-600 text-white shadow-sm'
                          : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
                      }`}
                      onClick={() => setDeliveryMode('ATTACHMENT')}
                    >
                      Attachment
                    </button>
                  </div>
                ) : (
                  <span className="text-sm text-slate-700 dark:text-slate-200">
                    {normalizedConfig.mode === 'ATTACHMENT_ONLY'
                      ? `Attachment (${normalizedConfig.attachmentFormat})`
                      : 'Inline'}
                  </span>
                )}
              </div>
              {footerNotes.length > 0 ? (
                <p className="text-xs text-red-600 dark:text-red-400">
                  {footerNotes.join(' • ')}
                </p>
              ) : (
                <p className="text-xs text-slate-500 dark:text-slate-400">{filterSummary || 'No filters applied'}</p>
              )}
              {error ? <p className="text-xs text-red-600 dark:text-red-400">{error}</p> : null}
            </div>

            <div className="flex items-center justify-end gap-2">
              <button
                type="button"
                className="inline-flex items-center rounded-md border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-100 dark:hover:bg-slate-800"
                onClick={onClose}
                disabled={submitting}
              >
                Cancel
              </button>
              <button
                type="button"
                className="inline-flex items-center rounded-md bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300 dark:bg-blue-500 dark:hover:bg-blue-400"
                onClick={handleSubmit}
                disabled={submitting}
              >
                {submitting ? 'Sending...' : 'Send Email'}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function normaliseDialogError(error: unknown): string {
  if (typeof error === 'string') {
    return error;
  }
  if (error && typeof error === 'object') {
    const maybeResponse = (error as { response?: { data?: unknown; statusText?: string } }).response;
    if (maybeResponse?.data && typeof maybeResponse.data === 'object') {
      const maybeMessage = (maybeResponse.data as { message?: unknown }).message;
      if (typeof maybeMessage === 'string') {
        return maybeMessage;
      }
    }
    if (typeof maybeResponse?.statusText === 'string' && maybeResponse.statusText.trim()) {
      return maybeResponse.statusText;
    }
  }
  return 'Unable to send report email.';
}
