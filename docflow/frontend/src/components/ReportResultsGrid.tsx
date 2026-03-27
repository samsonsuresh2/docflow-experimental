import { useMemo, useState } from 'react';

type Props = {
  columns: string[];
  rows: Array<Record<string, unknown>>;
  loading: boolean;
  error?: string | null;
  hasRun: boolean;
  page: number;
  pageSize: number;
  totalRows?: number;
  canGoNext: boolean;
  onPageChange: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
  fetchAllRows?: () => Promise<{ columns: string[]; rows: Array<Record<string, unknown>>; rowCount?: number }>;
};

const PAGE_SIZE_OPTIONS = [10, 25, 50, 100];

function toDisplayValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  if (value instanceof Date) {
    return value.toISOString();
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value);
    } catch (error) {
      return String(value);
    }
  }
  return String(value);
}

function escapeCsv(value: string): string {
  if (value.includes('"') || value.includes(',') || value.includes('\n')) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function downloadBlob(blob: Blob, filename: string) {
  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export default function ReportResultsGrid({
  columns,
  rows,
  loading,
  error,
  hasRun,
  page,
  pageSize,
  totalRows,
  canGoNext,
  onPageChange,
  onPageSizeChange,
  fetchAllRows,
}: Props) {
  const hasData = rows.length > 0 && columns.length > 0;
  const [exporting, setExporting] = useState(false);

  const csvContent = useMemo(() => {
    if (!hasData) {
      return '';
    }
    const header = columns.map((column) => escapeCsv(column)).join(',');
    const body = rows
      .map((row) => columns.map((column) => escapeCsv(toDisplayValue(row[column]))).join(','))
      .join('\n');
    return `${header}\n${body}`;
  }, [columns, rows, hasData]);

  const excelContent = useMemo(() => {
    if (!hasData) {
      return '';
    }
    const header = columns
      .map((column) => `<th style="text-align:left;padding:8px;">${escapeHtml(column)}</th>`)
      .join('');
    const body = rows
      .map((row) => {
        const cells = columns
          .map((column) => `<td style="padding:8px;">${escapeHtml(toDisplayValue(row[column]))}</td>`)
          .join('');
        return `<tr>${cells}</tr>`;
      })
      .join('');
    return `<!DOCTYPE html><html><head><meta charset="utf-8" /></head><body><table>${header ? `<thead><tr>${header}</tr></thead>` : ''}<tbody>${body}</tbody></table></body></html>`;
  }, [columns, rows, hasData]);

  const handleExportCsv = async () => {
    if (!hasData) {
      return;
    }
    if (!fetchAllRows) {
      if (!csvContent) {
        return;
      }
      const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
      downloadBlob(blob, 'report.csv');
      return;
    }
    setExporting(true);
    try {
      const fullResult = await fetchAllRows();
      const header = fullResult.columns.map((column) => escapeCsv(column)).join(',');
      const body = fullResult.rows
        .map((row) => fullResult.columns.map((column) => escapeCsv(toDisplayValue(row[column]))).join(','))
        .join('\n');
      const blob = new Blob([`${header}\n${body}`], { type: 'text/csv;charset=utf-8;' });
      downloadBlob(blob, 'report.csv');
    } finally {
      setExporting(false);
    }
  };

  const handleExportExcel = async () => {
    if (!hasData) {
      return;
    }
    if (!fetchAllRows) {
      if (!excelContent) {
        return;
      }
      const blob = new Blob([`\ufeff${excelContent}`], {
        type: 'application/vnd.ms-excel;charset=utf-8;',
      });
      downloadBlob(blob, 'report.xls');
      return;
    }
    setExporting(true);
    try {
      const fullResult = await fetchAllRows();
      const header = fullResult.columns
        .map((column) => `<th style="text-align:left;padding:8px;">${escapeHtml(column)}</th>`)
        .join('');
      const body = fullResult.rows
        .map((row) => {
          const cells = fullResult.columns
            .map((column) => `<td style="padding:8px;">${escapeHtml(toDisplayValue(row[column]))}</td>`)
            .join('');
          return `<tr>${cells}</tr>`;
        })
        .join('');
      const fullExcelContent = `<!DOCTYPE html><html><head><meta charset="utf-8" /></head><body><table>${header ? `<thead><tr>${header}</tr></thead>` : ''}<tbody>${body}</tbody></table></body></html>`;
      const blob = new Blob([`\ufeff${fullExcelContent}`], {
        type: 'application/vnd.ms-excel;charset=utf-8;',
      });
      downloadBlob(blob, 'report.xls');
    } finally {
      setExporting(false);
    }
  };

  const handlePrevious = () => {
    if (page > 0 && !loading && !exporting) {
      onPageChange(page - 1);
    }
  };

  const handleNext = () => {
    if (canGoNext && !loading && !exporting) {
      onPageChange(page + 1);
    }
  };

  const handlePageSizeChange = (size: number) => {
    onPageSizeChange?.(size);
  };

  return (
    <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Results</h2>
          <p className="text-sm text-slate-600 dark:text-slate-300">
            {error
              ? error
              : loading || exporting
              ? 'Running report...'
              : hasData
              ? `Page ${page + 1}`
              : hasRun
              ? 'No rows returned for the current selection.'
              : 'Configure filters and run a report to see results.'}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <label className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
            Page Size
            <select
              className="rounded border border-slate-300 px-2 py-1 text-xs transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
              value={pageSize}
              onChange={(event) => handlePageSizeChange(Number(event.target.value))}
              disabled={loading || exporting}
            >
              {PAGE_SIZE_OPTIONS.map((size) => (
                <option key={size} value={size}>
                  {size}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            className="inline-flex items-center rounded border border-slate-300 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-slate-700 shadow-sm transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-100 dark:hover:bg-slate-800"
            onClick={handleExportCsv}
            disabled={!hasData || loading || exporting}
          >
            Export CSV
          </button>
          <button
            type="button"
            className="inline-flex items-center rounded border border-slate-300 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-slate-700 shadow-sm transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-100 dark:hover:bg-slate-800"
            onClick={handleExportExcel}
            disabled={!hasData || loading || exporting}
          >
            Export Excel
          </button>
        </div>
      </div>
      <div className="mt-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="flex gap-2">
          <button
            type="button"
            className="inline-flex items-center rounded border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-100 dark:hover:bg-slate-800"
            onClick={handlePrevious}
            disabled={page === 0 || loading || exporting}
          >
            Previous
          </button>
          <button
            type="button"
            className="inline-flex items-center rounded border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-100 dark:hover:bg-slate-800"
            onClick={handleNext}
            disabled={!canGoNext || loading || exporting}
          >
            Next
          </button>
        </div>
        {hasData ? (
          <span className="text-xs uppercase tracking-wide text-slate-500 dark:text-slate-300">
            {typeof totalRows === 'number'
              ? `Showing ${page * pageSize + 1}-${page * pageSize + rows.length} of ${totalRows}`
              : `Showing ${rows.length} row${rows.length === 1 ? '' : 's'}`}
          </span>
        ) : null}
      </div>
      <div className="mt-4 overflow-x-auto">
        <table className="min-w-full table-auto divide-y divide-slate-200 dark:divide-slate-700">
          <thead className="bg-slate-50 dark:bg-slate-800/60">
            <tr>
              {columns.map((column) => (
                <th
                  key={column}
                  scope="col"
                  className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300"
                >
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200 dark:divide-slate-700">
            {loading || exporting ? (
              <tr>
                <td colSpan={Math.max(columns.length, 1)} className="px-4 py-6 text-center text-sm text-slate-500 dark:text-slate-400">
                  Running report...
                </td>
              </tr>
            ) : error ? (
              <tr>
                <td colSpan={Math.max(columns.length, 1)} className="px-4 py-6 text-center text-sm text-red-600 dark:text-red-400">
                  {error}
                </td>
              </tr>
            ) : hasRun && !hasData ? (
              <tr>
                <td colSpan={Math.max(columns.length, 1)} className="px-4 py-6 text-center text-sm text-slate-500 dark:text-slate-400">
                  No results to display.
                </td>
              </tr>
            ) : (
              rows.map((row, index) => (
                <tr key={index} className="hover:bg-slate-50 transition-colors dark:hover:bg-slate-800/70">
                  {columns.map((column) => (
                    <td key={column} className="px-4 py-2 text-sm text-slate-700 dark:text-slate-200">
                      {toDisplayValue(row[column])}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
