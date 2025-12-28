import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../lib/api';
import { useUser } from '../lib/UserContext';

type RolesResponse = {
  userId: string;
  roles: string[];
};

export default function RoleSelection() {
  const navigate = useNavigate();
  const { user, setUser } = useUser();
  const [roles, setRoles] = useState<string[]>([]);
  const [selectedRole, setSelectedRole] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await api.get<RolesResponse>('/auth/roles');
        if (!active) return;
        setRoles(response.data.roles ?? []);
        setSelectedRole(response.data.roles?.[0] ?? '');
      } catch {
        if (active) {
          setRoles([]);
          setError('Unable to load roles for this user.');
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };
    void load();
    return () => {
      active = false;
    };
  }, []);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!selectedRole) {
      setError('Select a role to continue.');
      return;
    }
    try {
      await api.post('/auth/active-role', { role: selectedRole });
      if (user) {
        setUser({ ...user, role: selectedRole as typeof user.role });
      } else {
        setUser({ userId: '', role: selectedRole as typeof selectedRole });
      }
      navigate('/');
    } catch {
      setError('Unable to set active role.');
    }
  };

  if (!user) {
    return (
      <div className="rounded border border-amber-200 bg-amber-50 p-6 text-sm text-amber-800 shadow-sm transition-colors dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-100">
        Please log in first.
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-md space-y-6">
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <h1 className="text-xl font-semibold text-slate-800 dark:text-slate-100">Select Active Role</h1>
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
          Choose the role you will use for this session. You can change it by logging out and selecting again.
        </p>
        {loading ? (
          <p className="mt-4 text-sm text-slate-500 dark:text-slate-400">Loading roles…</p>
        ) : roles.length === 0 ? (
          <p className="mt-4 text-sm text-red-600 dark:text-red-400">No roles assigned. Contact an administrator.</p>
        ) : (
          <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
            <label className="block text-sm">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">Role</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
                value={selectedRole}
                onChange={(event) => setSelectedRole(event.target.value)}
              >
                {roles.map((role) => (
                  <option key={role} value={role}>
                    {role}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="submit"
              className="inline-flex w-full justify-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-400"
              disabled={!selectedRole}
            >
              Enter
            </button>
            {error ? <p className="text-xs text-red-600 dark:text-red-400">{error}</p> : null}
          </form>
        )}
      </div>
    </div>
  );
}
