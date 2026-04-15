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

const FIELD_LABELS: Record<keyof ReportMailRuntimeValues, string> = {
  to: 'To',
  cc: 'CC',
  subject: 'Subject',
  body: 'Body',
  disclaimer: 'Disclaimer',
};

function FieldBadge({ children, locked = false }: { children: string; locked?: boolean }) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium ${
        locked
          ? 'border-slate-300 bg-slate-100 text-slate-700 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200'
          : 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-400/30 dark:bg-blue-500/10 dark:text-blue-200'
      }`}
    >
      {locked ? 'Locked: ' : ''}
      {children}
    </span>
  );
}

function splitMultiline(value: string): string[] {
  return value
    .split('\n')
    .map((part) => part.trim())
    .filter(Boolean);
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
  const mandatoryTextBlocks = useMemo(
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

  const deliveryOptionsVisible = normalizedConfig.mode === 'INLINE_OR_ATTACHMENT';
  const editableRecipients = {
    to: splitEmailList(values.to),
    cc: splitEmailList(values.cc),
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/70 p-4" onClick={onClose} role="presentation">
      <div
        className="relative max-h-[92vh] w-full max-w-6xl overflow-hidden rounded-2xl bg-white shadow-2xl dark:bg-slate-900"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Report mail"
      >
        <div className="flex items-center justify-between border-b border-slate-200 bg-white px-5 py-4 dark:border-slate-700 dark:bg-slate-900">
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">Compose Email</p>
            <p className="text-lg font-semibold text-slate-900 dark:text-slate-100">{templateName}</p>
            <p className="text-sm text-slate-500 dark:text-slate-400">Send this report as an output action from the current results.</p>
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

        <div className="max-h-[78vh] overflow-auto bg-slate-100 p-5 dark:bg-slate-950">
          <div className="grid gap-5 xl:grid-cols-[minmax(0,1.75fr)_340px]">
            <div className="space-y-4">
              <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
                <div className="border-b border-slate-200 bg-slate-50 px-5 py-3 dark:border-slate-700 dark:bg-slate-900">
                  <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">Message</p>
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    Mandatory values are enforced by the backend and shown here as locked content.
                  </p>
                </div>

                <div className="divide-y divide-slate-200 dark:divide-slate-700">
                  {(['to', 'cc'] as const).map((fieldName) => {
                    const fieldConfig = normalizedConfig.fields[fieldName];
                    const mandatoryValues = mandatoryRecipients[fieldName];
                    const editableValues = editableRecipients[fieldName];
                    const editableValue = values[fieldName];
                    const placeholder =
                      fieldName === 'to'
                        ? 'Add recipients separated by commas'
                        : 'Add carbon copy recipients separated by commas';
                    return (
                      <div key={fieldName} className="grid gap-3 px-5 py-4 md:grid-cols-[88px_minmax(0,1fr)] md:items-start">
                        <div className="pt-2 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                          {FIELD_LABELS[fieldName]}
                        </div>
                        <div className="space-y-3">
                          {mandatoryValues.length > 0 ? (
                            <div className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 dark:border-slate-700 dark:bg-slate-950/60">
                              <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                                Mandatory recipients
                              </p>
                              <div className="mt-2 flex flex-wrap gap-2">
                                {mandatoryValues.map((address) => (
                                  <FieldBadge key={`${fieldName}-mandatory-${address}`} locked>
                                    {address}
                                  </FieldBadge>
                                ))}
                              </div>
                            </div>
                          ) : null}
                          <div>
                            <label className="block">
                              <span className="text-[11px] font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                                {fieldConfig.editable ? 'Editable recipients' : 'Configured recipients'}
                              </span>
                              <input
                                type="text"
                                className="mt-2 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 shadow-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 disabled:cursor-not-allowed disabled:bg-slate-100 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40 dark:disabled:bg-slate-800"
                                value={editableValue}
                                onChange={(event) => handleTextChange(fieldName, event.target.value)}
                                readOnly={!fieldConfig.editable}
                                disabled={!fieldConfig.editable}
                                placeholder={placeholder}
                              />
                            </label>
                            <div className="mt-2 flex flex-wrap gap-2">
                              {editableValues.map((address) => (
                                <FieldBadge key={`${fieldName}-editable-${address}`}>{address}</FieldBadge>
                              ))}
                            </div>
                            <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
                              {fieldConfig.editable
                                ? 'Editable recipients are sent together with the locked recipients above.'
                                : 'This recipient field is fixed by report configuration.'}
                            </p>
                          </div>
                        </div>
                      </div>
                    );
                  })}

                  {(['subject', 'body', 'disclaimer'] as const).map((fieldName) => {
                    const fieldConfig = normalizedConfig.fields[fieldName];
                    const mandatoryValue = mandatoryTextBlocks[fieldName];
                    const isTextArea = fieldName === 'body' || fieldName === 'disclaimer';
                    const previewLines = splitMultiline(mandatoryValue);
                    const baseClass =
                      'mt-2 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 shadow-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 disabled:cursor-not-allowed disabled:bg-slate-100 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40 dark:disabled:bg-slate-800';
                    return (
                      <div key={fieldName} className="grid gap-3 px-5 py-4 md:grid-cols-[88px_minmax(0,1fr)] md:items-start">
                        <div className="pt-2 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                          {FIELD_LABELS[fieldName]}
                        </div>
                        <div className="space-y-3">
                          {mandatoryValue.trim() ? (
                            <div className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 dark:border-slate-700 dark:bg-slate-950/60">
                              <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                                Mandatory content
                              </p>
                              <div className="mt-2 space-y-2">
                                {previewLines.map((line, index) => (
                                  <p key={`${fieldName}-mandatory-line-${index}`} className="text-sm text-slate-700 dark:text-slate-200">
                                    {line}
                                  </p>
                                ))}
                              </div>
                            </div>
                          ) : null}
                          <label className="block">
                            <span className="text-[11px] font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                              {fieldConfig.editable ? 'Editable content' : 'Configured content'}
                            </span>
                            {isTextArea ? (
                              <textarea
                                className={baseClass}
                                rows={fieldName === 'body' ? 7 : 4}
                                value={values[fieldName]}
                                onChange={(event) => handleTextChange(fieldName, event.target.value)}
                                readOnly={!fieldConfig.editable}
                                disabled={!fieldConfig.editable}
                              />
                            ) : (
                              <input
                                type="text"
                                className={baseClass}
                                value={values[fieldName]}
                                onChange={(event) => handleTextChange(fieldName, event.target.value)}
                                readOnly={!fieldConfig.editable}
                                disabled={!fieldConfig.editable}
                              />
                            )}
                          </label>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>

            <aside className="space-y-4">
              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">Report Output</p>
                <p className="mt-2 text-lg font-semibold text-slate-900 dark:text-slate-100">{templateName}</p>
                <div className="mt-4 space-y-3 text-sm text-slate-600 dark:text-slate-300">
                  <div>
                    <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Filter summary</p>
                    <p className="mt-1 leading-6">{filterSummary || 'No filters applied'}</p>
                  </div>
                  <div>
                    <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Delivery</p>
                    <p className="mt-1">
                      {normalizedConfig.mode === 'ATTACHMENT_ONLY'
                        ? `Attachment (${normalizedConfig.attachmentFormat})`
                        : deliveryOptionsVisible
                        ? deliveryMode === 'ATTACHMENT'
                          ? `Attachment (${normalizedConfig.attachmentFormat})`
                          : 'Inline'
                        : 'Inline'}
                    </p>
                  </div>
                </div>
              </div>

              {deliveryOptionsVisible ? (
                <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                  <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">Choose delivery mode</p>
                  <div className="mt-3 grid gap-3">
                    <label
                      className={`rounded-xl border px-3 py-3 text-sm transition ${
                        deliveryMode === 'INLINE'
                          ? 'border-blue-500 bg-blue-50 text-blue-800 dark:border-blue-400 dark:bg-blue-500/10 dark:text-blue-100'
                          : 'border-slate-200 bg-white text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200'
                      }`}
                    >
                      <input type="radio" checked={deliveryMode === 'INLINE'} onChange={() => setDeliveryMode('INLINE')} className="sr-only" />
                      <span className="font-semibold">Inline</span>
                      <span className="mt-1 block text-xs text-slate-500 dark:text-slate-400">Paste the generated result into the email body.</span>
                    </label>
                    <label
                      className={`rounded-xl border px-3 py-3 text-sm transition ${
                        deliveryMode === 'ATTACHMENT'
                          ? 'border-blue-500 bg-blue-50 text-blue-800 dark:border-blue-400 dark:bg-blue-500/10 dark:text-blue-100'
                          : 'border-slate-200 bg-white text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200'
                      }`}
                    >
                      <input
                        type="radio"
                        checked={deliveryMode === 'ATTACHMENT'}
                        onChange={() => setDeliveryMode('ATTACHMENT')}
                        className="sr-only"
                      />
                      <span className="font-semibold">Attachment</span>
                      <span className="mt-1 block text-xs text-slate-500 dark:text-slate-400">
                        Send the generated result as a {normalizedConfig.attachmentFormat} attachment.
                      </span>
                    </label>
                  </div>
                </div>
              ) : (
                <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                  <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">Delivery mode</p>
                  <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
                    {normalizedConfig.mode === 'ATTACHMENT_ONLY'
                      ? `Attachment only (${normalizedConfig.attachmentFormat})`
                      : 'Inline only'}
                  </p>
                </div>
              )}

              {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}

              <div className="flex flex-wrap items-center justify-end gap-2 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                <button
                  type="button"
                  className="inline-flex items-center rounded border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-100 dark:hover:bg-slate-800"
                  onClick={onClose}
                  disabled={submitting}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="inline-flex items-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300 dark:bg-blue-500 dark:hover:bg-blue-400"
                  onClick={handleSubmit}
                  disabled={submitting}
                >
                  {submitting ? 'Sending...' : 'Send Email'}
                </button>
              </div>
            </aside>
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
