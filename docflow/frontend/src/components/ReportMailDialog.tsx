import { useEffect, useMemo, useState } from 'react';
import {
  joinEmailList,
  normalizeReportMailConfig,
  splitEmailList,
} from '../lib/reportMail';
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

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/70 p-4"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="relative max-h-[90vh] w-full max-w-3xl overflow-hidden rounded-lg bg-white shadow-xl dark:bg-slate-900"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Report mail"
      >
        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3 dark:border-slate-700">
          <div>
            <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">Email report</p>
            <p className="text-xs text-slate-500 dark:text-slate-400">{templateName}</p>
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

        <div className="max-h-[75vh] overflow-auto bg-slate-50 p-4 dark:bg-slate-950">
          <div className="space-y-4">
            <div className="rounded border border-slate-200 bg-white p-4 text-sm text-slate-700 shadow-sm dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200">
              <div className="font-semibold text-slate-800 dark:text-slate-100">Filter summary</div>
              <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">{filterSummary || 'No filters applied'}</div>
            </div>

            {deliveryOptionsVisible ? (
              <div className="rounded border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                <div className="text-sm font-semibold text-slate-800 dark:text-slate-100">Delivery mode</div>
                <div className="mt-3 flex flex-wrap gap-3 text-sm">
                  <label className="inline-flex items-center gap-2 text-slate-700 dark:text-slate-200">
                    <input
                      type="radio"
                      checked={deliveryMode === 'INLINE'}
                      onChange={() => setDeliveryMode('INLINE')}
                      className="h-4 w-4 border-slate-300 text-blue-600 focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-900"
                    />
                    Inline
                  </label>
                  <label className="inline-flex items-center gap-2 text-slate-700 dark:text-slate-200">
                    <input
                      type="radio"
                      checked={deliveryMode === 'ATTACHMENT'}
                      onChange={() => setDeliveryMode('ATTACHMENT')}
                      className="h-4 w-4 border-slate-300 text-blue-600 focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-900"
                    />
                    Attachment ({normalizedConfig.attachmentFormat})
                  </label>
                </div>
              </div>
            ) : (
              <div className="rounded border border-slate-200 bg-white p-4 text-sm text-slate-700 shadow-sm dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200">
                Delivery mode: {normalizedConfig.mode === 'ATTACHMENT_ONLY' ? `Attachment (${normalizedConfig.attachmentFormat})` : 'Inline'}
              </div>
            )}

            <div className="space-y-3">
              {(['to', 'cc', 'subject', 'body', 'disclaimer'] as const).map((fieldName) => {
                const fieldConfig = normalizedConfig.fields[fieldName];
                const isTextArea = fieldName === 'body' || fieldName === 'disclaimer';
                const baseClass =
                  'mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 disabled:cursor-not-allowed disabled:bg-slate-100 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40 dark:disabled:bg-slate-800';
                return (
                  <label key={fieldName} className="block rounded border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                    <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
                      {FIELD_LABELS[fieldName]}
                      {fieldConfig.mandatory.trim() ? <span className="ml-1 text-red-500">*</span> : null}
                    </span>
                    {isTextArea ? (
                      <textarea
                        className={baseClass}
                        rows={fieldName === 'body' ? 4 : 3}
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
                    {fieldConfig.mandatory.trim() ? (
                      <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
                        Mandatory content will be enforced by the backend: {fieldConfig.mandatory}
                      </p>
                    ) : null}
                  </label>
                );
              })}
            </div>

            {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}

            <div className="flex flex-wrap items-center justify-end gap-2">
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
