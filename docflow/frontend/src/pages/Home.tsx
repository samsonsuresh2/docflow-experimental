import { Link } from 'react-router-dom';
import { useUser } from '../lib/UserContext';

export default function Home() {
  const { user } = useUser();

  return (
    <div className="space-y-6">
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold text-slate-800">Welcome to DocFlow</h1>
        <p className="mt-2 text-sm text-slate-600">
          {user
            ? `You are signed in as ${user.id} (${user.role}). Use the navigation to manage documents, configure upload fields, or review the audit trail.`
            : 'Choose a user profile to begin. Navigate to the Login page and pick a role to start working with documents.'}
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <FeatureCard
          title="Upload Documents"
          description="Submit new documents with metadata captured from the configurable upload form."
          to="/upload"
        />
        <FeatureCard
          title="Review Workflow"
          description="Search for documents, update metadata, and drive maker-checker approvals."
          to="/review"
        />
        <FeatureCard
          title="Administer Fields"
          description="Define upload field configuration using JSON to drive dynamic UI experiences."
          to="/admin"
        />
        <FeatureCard
          title="Audit Trail"
          description="Inspect field-level changes recorded throughout the document lifecycle."
          to="/audit"
        />
      </div>
    </div>
  );
}

function FeatureCard({ title, description, to }: { title: string; description: string; to: string }) {
  return (
    <Link
      to={to}
      className="flex h-full flex-col justify-between rounded border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:border-blue-400 hover:shadow"
    >
      <div>
        <h2 className="text-lg font-semibold text-slate-800">{title}</h2>
        <p className="mt-2 text-sm text-slate-600">{description}</p>
      </div>
      <span className="mt-4 text-sm font-semibold text-blue-600">Open →</span>
    </Link>
  );
}
