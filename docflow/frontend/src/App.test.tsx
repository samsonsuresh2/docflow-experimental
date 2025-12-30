/// <reference types="vitest" />
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { render, screen } from '@testing-library/react';
import App from './App';
import { ThemeProvider } from './lib/ThemeContext';
import { ModuleCodes, type ModuleCode } from './types/modules';

let mockAllowedModules: ModuleCode[] = [];
const mockRefresh = vi.fn();
const mockSetUser = vi.fn();

vi.mock('./lib/ModuleContext', () => ({
  useModules: () => ({
    allowedModules: mockAllowedModules,
    loading: false,
    error: null,
    refresh: mockRefresh,
    hasModule: (code: ModuleCode) => mockAllowedModules.includes(code),
  }),
}));

vi.mock('./lib/UserContext', () => ({
  useUser: () => ({
    user: { userId: 'samson', role: 'MAKER' },
    setUser: mockSetUser,
  }),
}));

vi.mock('./pages/Upload', () => ({ default: () => <div>Upload Workspace</div> }));
vi.mock('./pages/Review', () => ({ default: () => <div>Review Workspace</div> }));
vi.mock('./pages/DataInjector', () => ({ default: () => <div>Data Ingestor Workspace</div> }));
vi.mock('./pages/Admin', () => ({ default: () => <div>Admin Workspace</div> }));
vi.mock('./pages/Audit', () => ({ default: () => <div>Audit Workspace</div> }));
vi.mock('./pages/ReportsPage', () => ({ default: () => <div>Reports Workspace</div> }));
vi.mock('./pages/ReportBuilderPage', () => ({ default: () => <div>Report Builder Workspace</div> }));
vi.mock('./pages/RoleSelection', () => ({ default: () => <div>Role Selection</div> }));
vi.mock('./pages/Login', () => ({ default: () => <div>Login Page</div> }));

function renderApp(initialRoute = '/') {
  return render(
    <MemoryRouter initialEntries={[initialRoute]}>
      <ThemeProvider>
        <App />
      </ThemeProvider>
    </MemoryRouter>,
  );
}

describe('module-based UI visibility', () => {
  beforeEach(() => {
    mockAllowedModules = [];
    mockRefresh.mockClear();
    mockSetUser.mockClear();
  });

  it('hides navigation and homepage entries for unauthorized modules', () => {
    mockAllowedModules = [ModuleCodes.UPLOAD, ModuleCodes.REPORTS];

    renderApp();

    expect(screen.getByText('Upload')).toBeInTheDocument();
    expect(screen.getByText('Reports')).toBeInTheDocument();
    expect(screen.queryByText('Audit')).not.toBeInTheDocument();
    expect(screen.queryByText('Report Config')).not.toBeInTheDocument();
    expect(screen.queryByText('Admin')).not.toBeInTheDocument();
    expect(screen.getByText('Upload Documents')).toBeInTheDocument();
    expect(screen.getByText('Reports')).toBeInTheDocument();
    expect(screen.queryByText('Audit Trail')).not.toBeInTheDocument();
    expect(screen.queryByText('Administer Fields')).not.toBeInTheDocument();
  });

  it('renders allowed module routes when permitted', () => {
    mockAllowedModules = [ModuleCodes.REPORTS, ModuleCodes.REVIEW];

    renderApp('/reports');

    expect(screen.getByText('Reports Workspace')).toBeInTheDocument();
    expect(screen.queryByText('Not authorized to view this module.')).not.toBeInTheDocument();
  });

  it('keeps route guard for unauthorized modules', () => {
    mockAllowedModules = [ModuleCodes.REPORTS];

    renderApp('/admin');

    expect(screen.getByText('Not authorized to view this module.')).toBeInTheDocument();
  });
});
