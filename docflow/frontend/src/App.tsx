import { NavLink, Route, Routes, useNavigate } from 'react-router-dom';
import Login from './pages/Login';
import Home from './pages/Home';
import Upload from './pages/Upload';
import Review from './pages/Review';
import FastTrackApproval from './pages/FastTrackApproval';
import DataInjector from './pages/DataInjector';
import Admin from './pages/Admin';
import Audit from './pages/Audit';
import ReportsPage from './pages/ReportsPage';
import ReportBuilderPage from './pages/ReportBuilderPage';
import RoleSelection from './pages/RoleSelection';
import { useUser } from './lib/UserContext';
import ThemeToggle from './components/ThemeToggle';
import { useModules } from './lib/ModuleContext';
import { ModuleCodes, type ModuleCode } from './types/modules';
import NotAuthorized from './components/NotAuthorized';
import { t } from './i18n';

function App() {
  const navigate = useNavigate();
  const { user, setUser } = useUser();
  const { hasModule, loading: modulesLoading, error: modulesError } = useModules();

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
            {t('common.modulePortal')}
          </button>
          <nav className="flex flex-wrap items-center gap-2">
            <NavLink to="/" className={navLinkClass} end>
              {t('common.home')}
            </NavLink>
            {hasModule(ModuleCodes.UPLOAD) ? (
              <NavLink to="/upload" className={navLinkClass}>
                {t('common.upload')}
              </NavLink>
            ) : null}
            {hasModule(ModuleCodes.REVIEW) ? (
              <NavLink to="/review" className={navLinkClass}>
                {t('common.review')}
              </NavLink>
            ) : null}
            {hasModule(ModuleCodes.FAST_TRACK_APPROVAL) ? (
              <NavLink to="/fast-track-approval" className={navLinkClass}>
                {t('common.fastTrackApproval')}
              </NavLink>
            ) : null}
            {hasModule(ModuleCodes.AUDIT) ? (
              <NavLink to="/audit" className={navLinkClass}>
                {t('common.audit')}
              </NavLink>
            ) : null}
            {hasModule(ModuleCodes.REPORTS) ? (
              <NavLink to="/reports" className={navLinkClass}>
                {t('common.reports')}
              </NavLink>
            ) : null}
            {hasModule(ModuleCodes.REPORT_CONFIG) ? (
              <NavLink to="/report-config" className={navLinkClass}>
                {t('common.reportConfig')}
              </NavLink>
            ) : null}
            {hasModule(ModuleCodes.ADMIN) ? (
              <NavLink to="/admin" className={navLinkClass}>
                {t('common.admin')}
              </NavLink>
            ) : null}
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
                  {t('common.logout')}
                </button>
              </>
            ) : (
              <button
                type="button"
                className="rounded bg-blue-600 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-white transition hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-400"
                onClick={() => navigate('/login')}
              >
                {t('common.login')}
              </button>
            )}
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-8">
        {modulesError ? <NotAuthorized message={t('common.moduleAccessLoadFailed')} /> : null}
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/select-role" element={<RoleSelection />} />
          <Route
            path="/upload"
            element={
              <ModuleGate module={ModuleCodes.UPLOAD}>
                <Upload />
              </ModuleGate>
            }
          />
          <Route
            path="/review"
            element={
              <ModuleGate module={ModuleCodes.REVIEW}>
                <Review />
              </ModuleGate>
            }
          />
          <Route
            path="/fast-track-approval"
            element={
              <ModuleGate module={ModuleCodes.FAST_TRACK_APPROVAL}>
                <FastTrackApproval />
              </ModuleGate>
            }
          />
          <Route
            path="/data-ingestor"
            element={
              <ModuleGate module={ModuleCodes.DATA_INGEST}>
                <DataInjector />
              </ModuleGate>
            }
          />
          <Route
            path="/admin"
            element={
              <ModuleGate module={ModuleCodes.ADMIN}>
                <Admin />
              </ModuleGate>
            }
          />
          <Route
            path="/audit"
            element={
              <ModuleGate module={ModuleCodes.AUDIT}>
                <Audit />
              </ModuleGate>
            }
          />
          <Route
            path="/reports"
            element={
              <ModuleGate module={ModuleCodes.REPORTS}>
                <ReportsPage />
              </ModuleGate>
            }
          />
          <Route
            path="/report-config"
            element={
              <ModuleGate module={ModuleCodes.REPORT_CONFIG}>
                <ReportBuilderPage />
              </ModuleGate>
            }
          />
        </Routes>
      </main>
    </div>
  );
}

export default App;

function ModuleGate({ children, module }: { children: JSX.Element; module: ModuleCode }) {
  const { hasModule, loading } = useModules();
  if (loading) {
    return <div className="text-sm text-slate-500 dark:text-slate-400">{t('common.loadingModuleAccess')}</div>;
  }
  if (!hasModule(module)) {
    return <NotAuthorized />;
  }
  return children;
}
