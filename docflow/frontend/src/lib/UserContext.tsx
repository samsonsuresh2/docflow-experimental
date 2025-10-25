import { createContext, type ReactNode, useContext, useMemo, useState } from 'react';
import { loadUser, persistUser, UserProfile } from './user';

type UserContextValue = {
  user: UserProfile | null;
  setUser: (user: UserProfile | null) => void;
};

const UserContext = createContext<UserContextValue | undefined>(undefined);

export function UserProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<UserProfile | null>(() => loadUser());

  const setUser = (next: UserProfile | null) => {
    setUserState(next);
    persistUser(next);
  };

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
