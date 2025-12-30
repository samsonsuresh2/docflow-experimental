import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useUser } from './UserContext';
import type { ModuleCode } from '../types/modules';
import { clearSessionContextCache, getSessionContext } from './session';

type ModuleContextValue = {
  allowedModules: ModuleCode[];
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  hasModule: (code: ModuleCode) => boolean;
};

const ModuleContext = createContext<ModuleContextValue | undefined>(undefined);

export function ModuleProvider({ children }: { children: ReactNode }) {
  const { user } = useUser();
  const [allowedModules, setAllowedModules] = useState<ModuleCode[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const loadModules = useCallback(async (forceRefresh?: boolean) => {
    if (!user) {
      setAllowedModules([]);
      setError(null);
      clearSessionContextCache();
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const session = await getSessionContext({ forceRefresh });
      setAllowedModules(session.allowedModules);
    } catch (err) {
      setAllowedModules([]);
      setError('Unable to load modules for this user.');
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    void loadModules();
  }, [user?.userId, user?.role]);

  const value = useMemo<ModuleContextValue>(
    () => ({
      allowedModules,
      loading,
      error,
      refresh: () => loadModules(true),
      hasModule: (code: ModuleCode) => allowedModules.includes(code),
    }),
    [allowedModules, loading, error, loadModules],
  );

  return <ModuleContext.Provider value={value}>{children}</ModuleContext.Provider>;
}

export function useModules(): ModuleContextValue {
  const ctx = useContext(ModuleContext);
  if (!ctx) {
    throw new Error('useModules must be used within ModuleProvider');
  }
  return ctx;
}
