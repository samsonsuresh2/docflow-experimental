import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useUser } from '../lib/UserContext';
import { findUserById, USER_OPTIONS } from '../lib/user';

export default function Login() {
  const navigate = useNavigate();
  const { user, setUser } = useUser();
  const [selectedUserId, setSelectedUserId] = useState<string>(user?.userId ?? USER_OPTIONS[0].userId);

  useEffect(() => {
    if (user) {
      setSelectedUserId(user.userId);
    }
  }, [user]);

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    const profile = findUserById(selectedUserId);
    if (!profile) {
      return;
    }
    setUser(profile);
    navigate('/');
  };

  return (
    <div className="mx-auto max-w-md space-y-6">
      <div className="rounded border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-800">Select a user profile</h1>
        <p className="mt-2 text-sm text-slate-600">
          DocFlow relies on the <code className="rounded bg-slate-100 px-1">X-USER-ID</code> header. Pick a seeded Oracle user
          to impersonate during development.
        </p>
        <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
          <label className="block text-sm">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-600">User</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-blue-500 focus:outline-none focus:ring focus:ring-blue-200"
              value={selectedUserId}
              onChange={(event) => setSelectedUserId(event.target.value)}
            >
              {USER_OPTIONS.map((option) => (
                <option key={option.userId} value={option.userId}>
                  {option.userId} · {option.role}
                </option>
              ))}
            </select>
          </label>
          <button
            type="submit"
            className="inline-flex w-full justify-center rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-blue-700"
          >
            Continue
          </button>
        </form>
      </div>
    </div>
  );
}
