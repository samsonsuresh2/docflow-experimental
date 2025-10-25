import { NavLink, Route, Routes, useNavigate } from 'react-router-dom';
import Login from './pages/Login';
import Home from './pages/Home';
import Upload from './pages/Upload';
import Review from './pages/Review';
import Admin from './pages/Admin';
import Audit from './pages/Audit';
import { useUser } from './lib/UserContext';

function App() {
  const navigate = useNavigate();
  const { user, setUser } = useUser();

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `rounded px-3 py-2 text-sm font-medium transition hover:bg-white/10 ${
      isActive ? 'bg-white/20 text-white' : 'text-white/80'
    }`;

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="bg-slate-900 text-white shadow">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-6 py-4">
          <button
            type="button"
            className="text-lg font-semibold"
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
          <div className="flex items-center gap-2 text-sm">
            {user ? (
              <>
                <span className="rounded bg-slate-800 px-2 py-1 text-xs uppercase tracking-wide text-slate-100">
                  {user.id} · {user.role}
                </span>
                <button
                  type="button"
                  onClick={() => {
                    setUser(null);
                    navigate('/login');
                  }}
                  className="rounded bg-white/10 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-white transition hover:bg-white/20"
                >
                  Logout
                </button>
              </>
            ) : (
              <button
                type="button"
                className="rounded bg-white/10 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-white transition hover:bg-white/20"
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
          <Route path="/admin" element={<Admin />} />
          <Route path="/audit" element={<Audit />} />
        </Routes>
      </main>
    </div>
  );
}

export default App;
