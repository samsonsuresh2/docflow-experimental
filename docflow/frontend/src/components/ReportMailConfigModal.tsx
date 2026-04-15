import { useEffect, useState } from 'react';
import ReportMailConfigEditor from './ReportMailConfigEditor';
import type { ReportMailConfig } from '../types/reports';

type Props = {
  isOpen: boolean;
  onClose: () => void;
  onSave: (value: ReportMailConfig) => void;
  value: ReportMailConfig;
  templateName: string;
};

export default function ReportMailConfigModal({ isOpen, onClose, onSave, value, templateName }: Props) {
  const [draft, setDraft] = useState<ReportMailConfig>(value);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    setDraft(value);
  }, [isOpen, value]);

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

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/70 p-4"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="relative max-h-[90vh] w-full max-w-5xl overflow-hidden rounded-lg bg-white shadow-xl dark:bg-slate-900"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Report email configuration"
      >
        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3 dark:border-slate-700">
          <div>
            <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">Configure email output</p>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              {templateName.trim() ? templateName : 'Unsaved report template'}
            </p>
          </div>
          <button
            type="button"
            className="inline-flex h-8 w-8 items-center justify-center rounded-full text-slate-500 transition hover:bg-slate-100 hover:text-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
            onClick={onClose}
            aria-label="Close email configuration"
          >
            <span aria-hidden="true">x</span>
          </button>
        </div>

        <div className="max-h-[75vh] overflow-auto bg-slate-50 p-4 dark:bg-slate-950">
          <div className="mb-4 rounded border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">Email is an output add-on</p>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Configure runtime defaults here while keeping the main report definition focused on filters, columns, and data shape.
            </p>
          </div>

          <ReportMailConfigEditor value={draft} onChange={setDraft} showEnabledToggle={false} />
        </div>

        <div className="flex flex-wrap items-center justify-end gap-2 border-t border-slate-200 px-4 py-3 dark:border-slate-700">
          <button
            type="button"
            className="inline-flex items-center rounded border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-100 dark:border-slate-600 dark:text-slate-100 dark:hover:bg-slate-800"
            onClick={onClose}
          >
            Cancel
          </button>
          <button
            type="button"
            className="inline-flex items-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-400"
            onClick={() => {
              onSave(draft);
              onClose();
            }}
          >
            Save Email Settings
          </button>
        </div>
      </div>
    </div>
  );
}
