import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { persistUser, UserProfile } from './user';
import { clearSessionContextCache, getSessionContext } from './session';

type UserContextValue = {
  user: UserProfile | null;
  setUser: (user: UserProfile | null, options?: { preserveSessionCache?: boolean }) => void;
};

const UserContext = createContext<UserContextValue | undefined>(undefined);

export function UserProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<UserProfile | null>(null);

  const setUser = (next: UserProfile | null, options?: { preserveSessionCache?: boolean }) => {
    if (!options?.preserveSessionCache) {
      if (!next || user?.userId !== next.userId || user?.role !== next.role) {
        clearSessionContextCache();
      }
    }
    setUserState(next);
    persistUser(next);
  };

  useEffect(() => {
    let active = true;
    const bootstrap = async () => {
      try {
        const session = await getSessionContext();
        if (!active) return;
        if (session.userId) {
          setUser({
            userId: session.userId,
            role: (session.activeRole ?? '') as UserProfile['role'],
          }, { preserveSessionCache: true });
        }
      } catch {
        if (active) {
          setUser(null);
        }
      }
    };
    void bootstrap();
    return () => {
      active = false;
    };
  }, []);

  const value = useMemo<UserContextValue>(() => ({ user, setUser }), [user]);

  return <UserContext.Provider value={value}>{children}</UserContext.Provider>;
}

export function useUser(): UserContextValue {
  const context = useContext(UserContext);
  if (!context) {
    throw new Error('useUser must be used within a UserProvider');
  }
  return context;
}
