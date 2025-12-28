import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useUser } from '../lib/UserContext';
import api from '../lib/api';

export default function Login() {
  const navigate = useNavigate();
  const { user, setUser } = useUser();
  const [userId, setUserId] = useState<string>(user?.userId ?? '');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (user) {
      setUserId(user.userId);
    }
  }, [user]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!userId.trim()) {
      setError('Enter a user ID to continue.');
      return;
    }
    try {
      await api.post('/auth/dev-login', { userId: userId.trim() });
      setUser({ userId: userId.trim(), role: '' });
      navigate('/select-role');
    } catch {
      setError('Login failed. Try again.');
    }
  };

  return (
    <div className="mx-auto max-w-md space-y-6">
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm transition-colors dark:border-slate-700 dark:bg-slate-900">
        <h1 className="text-xl font-semibold text-slate-800 dark:text-slate-100">Developer Login</h1>
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">Enter a user ID to start a session, then choose your active role.</p>
        <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
          <label className="block text-sm">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">User ID</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm transition-colors focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-400 dark:focus:ring-blue-500/40"
              value={userId}
              onChange={(event) => setUserId(event.target.value)}
              placeholder="e.g. samson"
            />
          </label>
          <button
            type="submit"
            className="inline-flex w-full justify-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-400"
          >
            Continue
          </button>
          {error ? <p className="text-xs text-red-600 dark:text-red-400">{error}</p> : null}
        </form>
      </div>
    </div>
  );
}
