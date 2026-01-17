import { Link } from 'react-router-dom';
import { useUser } from '../lib/UserContext';
import { useModules } from '../lib/ModuleContext';
import { ModuleCodes, type ModuleCode } from '../types/modules';

export default function Home() {
  const { user } = useUser();
  const { hasModule, loading: modulesLoading } = useModules();

  const features: Array<{ title: string; description: string; to: string; module: ModuleCode }> = [
    {
      title: 'Upload Documents',
      description: 'Submit new documents with metadata captured from the configurable upload form.',
      to: '/upload',
      module: ModuleCodes.UPLOAD,
    },
    {
      title: 'Review Workflow',
      description: 'Search for documents, update metadata, and drive maker-checker approvals.',
      to: '/review',
      module: ModuleCodes.REVIEW,
    },
    {
      title: 'Fast-Track Approval',
      description: 'Bulk approve, hold, or reject documents that have completed review.',
      to: '/fast-track-approval',
      module: ModuleCodes.FAST_TRACK_APPROVAL,
    },
    {
      title: 'Administer Fields',
      description: 'Define upload field configuration using JSON to drive dynamic UI experiences.',
      to: '/admin',
      module: ModuleCodes.ADMIN,
    },
    {
      title: 'Audit Trail',
      description: 'Inspect field-level changes recorded throughout the document lifecycle.',
      to: '/audit',
      module: ModuleCodes.AUDIT,
    },
    {
      title: 'Reports',
      description: 'Build ad-hoc reports across DocFlow data using the dynamic report designer.',
      to: '/reports',
      module: ModuleCodes.REPORTS,
    },
    {
      title: 'Data Ingestor',
      description: 'Bulk import or update records via spreadsheet uploads to accelerate onboarding.',
      to: '/data-ingestor',
      module: ModuleCodes.DATA_INGEST,
    },
    {
      title: 'Report Config',
      description: 'Create and modify report templates available to your teams.',
      to: '/report-config',
      module: ModuleCodes.REPORT_CONFIG,
    },
  ];

  const visibleFeatures = features.filter((feature) => hasModule(feature.module));

  return (
    <div className="space-y-6">
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <h1 className="text-2xl font-semibold text-slate-800 dark:text-slate-100">Welcome to DocFlow</h1>
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
          {user
            ? `You are signed in as ${user.userId} (${user.role}). Use the navigation to manage documents, configure upload fields, or review the audit trail.`
            : 'Choose a user profile to begin. Navigate to the Login page and pick a role to start working with documents.'}
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {modulesLoading ? (
          <div className="rounded border border-slate-200 bg-white p-4 text-sm text-slate-600 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200">
            Loading modules…
          </div>
        ) : null}
        {visibleFeatures.map((feature) => (
          <FeatureCard key={feature.to} title={feature.title} description={feature.description} to={feature.to} />
        ))}
        {!modulesLoading && visibleFeatures.length === 0 ? (
          <div className="rounded border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 shadow-sm transition-colors dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-100">
            No modules are available for your current role. Switch roles or contact an administrator for access.
          </div>
        ) : null}
      </div>
    </div>
  );
}

function FeatureCard({ title, description, to }: { title: string; description: string; to: string }) {
  return (
    <Link
      to={to}
      className="flex h-full flex-col justify-between rounded border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:border-blue-400 hover:shadow dark:border-slate-700 dark:bg-slate-900"
    >
      <div>
        <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">{title}</h2>
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">{description}</p>
      </div>
      <span className="mt-4 text-sm font-semibold text-blue-600 dark:text-blue-400">Open →</span>
    </Link>
  );
}
