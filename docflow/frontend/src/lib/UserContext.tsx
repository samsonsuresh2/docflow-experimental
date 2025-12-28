import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { persistUser, UserProfile } from './user';
import api from './api';

type UserContextValue = {
  user: UserProfile | null;
  setUser: (user: UserProfile | null) => void;
};

const UserContext = createContext<UserContextValue | undefined>(undefined);

export function UserProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<UserProfile | null>(null);

  const setUser = (next: UserProfile | null) => {
    setUserState(next);
    persistUser(next);
  };

  useEffect(() => {
    let active = true;
    const bootstrap = async () => {
      try {
        const response = await api.get<{ userId: string; activeRole: string | null }>('/auth/me');
        if (!active) return;
        if (response.data.userId) {
          setUser({
            userId: response.data.userId,
            role: (response.data.activeRole ?? '') as UserProfile['role'],
          });
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
