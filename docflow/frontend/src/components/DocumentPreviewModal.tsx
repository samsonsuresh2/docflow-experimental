import { useEffect } from 'react';

export type PreviewContent =
  | { kind: 'pdf'; url: string }
  | { kind: 'text'; text: string }
  | { kind: 'docx'; html: string }
  | { kind: 'xlsx'; html: string }
  | { kind: 'unsupported'; message: string };

interface DocumentPreviewModalProps {
  isOpen: boolean;
  onClose: () => void;
  loading: boolean;
  error: string | null;
  content: PreviewContent | null;
  downloadUrl: string | null;
  fileName: string | null;
}

export default function DocumentPreviewModal({
  isOpen,
  onClose,
  loading,
  error,
  content,
  downloadUrl,
  fileName,
}: DocumentPreviewModalProps) {
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

  const handleDownload = () => {
    if (!downloadUrl) {
      return;
    }
    const anchor = document.createElement('a');
    anchor.href = downloadUrl;
    anchor.download = fileName ?? 'document';
    anchor.rel = 'noopener noreferrer';
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/70 p-4"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="relative max-h-[90vh] w-full max-w-4xl overflow-hidden rounded-lg bg-white shadow-xl dark:bg-slate-900"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Document preview"
      >
        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3 dark:border-slate-700">
          <div>
            <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">{fileName ?? 'Document preview'}</p>
            <p className="text-xs text-slate-500 dark:text-slate-400">Preview the uploaded file before taking action.</p>
          </div>
          <div className="flex items-center gap-2">
            {downloadUrl ? (
              <button
                type="button"
                className="inline-flex items-center rounded border border-slate-300 px-3 py-1 text-xs font-semibold text-slate-700 transition hover:bg-slate-100 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
                onClick={handleDownload}
              >
                Download
              </button>
            ) : null}
            <button
              type="button"
              className="inline-flex h-8 w-8 items-center justify-center rounded-full text-slate-500 transition hover:bg-slate-100 hover:text-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
              onClick={onClose}
              aria-label="Close preview"
            >
              <span aria-hidden="true">×</span>
            </button>
          </div>
        </div>
        <div className="max-h-[75vh] overflow-auto bg-slate-50 p-4 dark:bg-slate-950">
          {loading ? (
            <p className="text-sm text-slate-600 dark:text-slate-300">Loading preview…</p>
          ) : error ? (
            <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
          ) : !content ? (
            <p className="text-sm text-slate-600 dark:text-slate-300">No preview content available.</p>
          ) : null}

          {!loading && !error && content ? (
            <PreviewRenderer content={content} />
          ) : null}
        </div>
      </div>
    </div>
  );
}

function PreviewRenderer({ content }: { content: PreviewContent }) {
  switch (content.kind) {
    case 'pdf':
      return (
        <iframe
          title="PDF preview"
          src={content.url}
          className="h-[70vh] w-full rounded border border-slate-200 bg-white"
        />
      );
    case 'text':
      return (
        <pre className="whitespace-pre-wrap text-sm text-slate-800 dark:text-slate-200">{content.text}</pre>
      );
    case 'docx':
      return (
        <div
          className="space-y-2 text-sm text-slate-800 dark:text-slate-100"
          dangerouslySetInnerHTML={{ __html: content.html }}
        />
      );
    case 'xlsx':
      return (
        <div
          className="overflow-x-auto text-sm text-slate-800 dark:text-slate-100"
          dangerouslySetInnerHTML={{ __html: content.html }}
        />
      );
    case 'unsupported':
      return <p className="text-sm text-slate-600 dark:text-slate-300">{content.message}</p>;
    default:
      return null;
  }
}
