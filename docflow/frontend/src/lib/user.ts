export type UserRole = 'ADMIN' | 'MAKER' | 'REVIEWER' | 'CHECKER' | 'APPROVER' | '';

export interface UserProfile {
  userId: string;
  role: UserRole;
}

const STORAGE_KEY = 'docflow:user';

export const USER_OPTIONS: UserProfile[] = [];

export function findUserById(): UserProfile | null {
  return null;
}

export function loadUser(): UserProfile | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<UserProfile>;
    if (parsed && parsed.userId) {
      return { userId: parsed.userId, role: (parsed.role ?? '') as UserRole };
    }
  } catch {
    return null;
  }
  return null;
}

export function persistUser(user: UserProfile | null): void {
  if (typeof window === 'undefined') {
    return;
  }
  if (!user) {
    window.localStorage.removeItem(STORAGE_KEY);
    return;
  }
  window.localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({ userId: user.userId, role: user.role }),
  );
}
