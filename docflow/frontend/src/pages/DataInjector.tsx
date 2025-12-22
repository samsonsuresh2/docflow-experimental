import { ChangeEvent, FormEvent, useState } from 'react';
import api from '../lib/api';
import { useUser } from '../lib/UserContext';
import type { DataInjectorResponse } from '../types/dataInjector';

export default function DataInjector() {
  const { user } = useUser();
  const [file, setFile] = useState<File | null>(null);
  const [fileInputKey, setFileInputKey] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [result, setResult] = useState<DataInjectorResponse | null>(null);

  if (!user) {
    return <AuthRequired />;
  }

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const selectedFile = event.target.files?.[0] ?? null;
    setFile(selectedFile);
    setErrorMessage(null);
    setResult(null);
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!file) {
      return;
    }

    setUploading(true);
    setErrorMessage(null);
    setResult(null);

    const formData = new FormData();
    formData.append('file', file);

    try {
      const response = await api.post<DataInjectorResponse>(
        '/documents/data-injector/uploadexcel',
        formData,
        {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        },
      );
      setResult(response.data);
      setFile(null);
      setFileInputKey((value) => value + 1);
    } catch (error) {
      setErrorMessage('Upload failed — please check file format or contact admin.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <h1 className="text-xl font-semibold text-slate-800 dark:text-slate-100">Data Ingestor</h1>
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
          Upload a prepared Excel workbook to bulk insert or update document records. Each row should include a
          <span className="mx-1 rounded bg-slate-100 px-1 font-mono text-xs dark:bg-slate-800">DocumentNumber</span>
          column and optional metadata columns.
        </p>
      </div>

      <form
        className="space-y-4 rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900"
        onSubmit={handleSubmit}
      >
        <fieldset className="space-y-3" disabled={uploading}>
          <legend className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-300">
            Upload Spreadsheet
          </legend>
          <label className="block text-sm">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
              Excel file
            </span>
            <input
              key={fileInputKey}
              type="file"
              accept=".xls,.xlsx"
              className="mt-1 block w-full text-sm text-slate-600 file:mr-4 file:rounded file:border-0 file:bg-blue-600 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-white file:transition file:hover:bg-blue-700 dark:text-slate-300 dark:file:bg-blue-500 dark:file:hover:bg-blue-400"
              onChange={handleFileChange}
            />
          </label>
        </fieldset>

        {errorMessage ? <p className="text-sm text-red-600 dark:text-red-400">{errorMessage}</p> : null}

        <button
          type="submit"
          className="inline-flex items-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300 dark:bg-blue-500 dark:hover:bg-blue-400 dark:disabled:bg-blue-500/60"
          disabled={!file || uploading}
        >
          {uploading ? (
            <>
              <span className="mr-2 inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-r-transparent" />
              Uploading…
            </>
          ) : (
            'Upload'
          )}
        </button>
      </form>

      {result ? (
        <div className="space-y-4 rounded border border-emerald-200 bg-emerald-50 p-6 text-sm text-emerald-800 shadow-sm transition-colors dark:border-emerald-500/40 dark:bg-emerald-500/10 dark:text-emerald-200">
          <div className="text-base font-semibold">
            ✅ Data Injection Complete
          </div>
          <dl className="grid gap-2 sm:grid-cols-2">
            <div>
              <dt className="text-xs uppercase tracking-wide text-emerald-600 dark:text-emerald-300">Total Rows</dt>
              <dd className="text-base font-semibold">{result.totalRows}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-emerald-600 dark:text-emerald-300">Inserted</dt>
              <dd className="text-base font-semibold">{result.inserted}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-emerald-600 dark:text-emerald-300">Updated</dt>
              <dd className="text-base font-semibold">{result.updated}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-emerald-600 dark:text-emerald-300">Skipped</dt>
              <dd className="text-base font-semibold">{result.skipped}</dd>
            </div>
          </dl>
          <div>
            <div className="text-xs uppercase tracking-wide text-emerald-600 dark:text-emerald-300">Ignored Columns</div>
            <div className="mt-1 font-semibold">
              {result.ignoredColumns.length > 0 ? result.ignoredColumns.join(', ') : 'None'}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function AuthRequired() {
  return (
    <div className="rounded border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-200">
      Please sign in to access the Data Ingestor.
    </div>
  );
}
