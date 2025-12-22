import { NavLink, Route, Routes, useNavigate } from 'react-router-dom';
import Login from './pages/Login';
import Home from './pages/Home';
import Upload from './pages/Upload';
import Review from './pages/Review';
import DataInjector from './pages/DataInjector';
import Admin from './pages/Admin';
import Audit from './pages/Audit';
import ReportBuilderPage from './pages/ReportBuilderPage';
import { useUser } from './lib/UserContext';
import ThemeToggle from './components/ThemeToggle';

function App() {
  const navigate = useNavigate();
  const { user, setUser } = useUser();

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    [
      'rounded px-3 py-2 text-sm font-medium transition-colors',
      isActive
        ? 'bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-100'
        : 'text-slate-700 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-200 dark:hover:bg-slate-800 dark:hover:text-slate-100',
    ].join(' ');

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 transition-colors dark:bg-slate-950 dark:text-slate-100">
      <header className="bg-white text-slate-900 shadow transition-colors dark:bg-slate-900 dark:text-slate-100">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-6 py-4">
          <button
            type="button"
            className="text-lg font-semibold transition-colors hover:text-blue-600 dark:hover:text-blue-300"
            onClick={() => navigate('/')}
          >
            DocFlow Portal
          </button>
          <nav className="flex flex-wrap items-center gap-2">
            <NavLink to="/" className={navLinkClass} end>
              Home
            </NavLink>
            <NavLink to="/upload" className={navLinkClass}>
              Upload
            </NavLink>
            <NavLink to="/review" className={navLinkClass}>
              Review
            </NavLink>
            <NavLink to="/audit" className={navLinkClass}>
              Audit
            </NavLink>
            <NavLink to="/admin" className={navLinkClass}>
              Admin
            </NavLink>
          </nav>
          <div className="flex items-center gap-3 text-sm">
            <ThemeToggle />
            {user ? (
              <>
                <span className="rounded bg-slate-200 px-2 py-1 text-xs uppercase tracking-wide text-slate-700 dark:bg-slate-800 dark:text-slate-200">
                  {user.userId} · {user.role}
                </span>
                <button
                  type="button"
                  onClick={() => {
                    setUser(null);
                    navigate('/login');
                  }}
                  className="rounded bg-blue-600 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-white transition hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-400"
                >
                  Logout
                </button>
              </>
            ) : (
              <button
                type="button"
                className="rounded bg-blue-600 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-white transition hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-400"
                onClick={() => navigate('/login')}
              >
                Login
              </button>
            )}
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-8">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/upload" element={<Upload />} />
          <Route path="/review" element={<Review />} />
          <Route path="/data-ingestor" element={<DataInjector />} />
          <Route path="/admin" element={<Admin />} />
          <Route path="/audit" element={<Audit />} />
          <Route path="/reports" element={<ReportBuilderPage />} />
        </Routes>
      </main>
    </div>
  );
}

export default App;
