export type UserRole = 'ADMIN' | 'MAKER' | 'REVIEWER' | 'CHECKER';

export interface UserProfile {
  id: string;
  role: UserRole;
}

const STORAGE_KEY = 'docflow:user';

export const USER_OPTIONS: UserProfile[] = [
  { id: 'admin1', role: 'ADMIN' },
  { id: 'maker1', role: 'MAKER' },
  { id: 'reviewer1', role: 'REVIEWER' },
  { id: 'checker1', role: 'CHECKER' },
];

export function findUserById(id: string | null | undefined): UserProfile | null {
  if (!id) {
    return null;
  }
  return USER_OPTIONS.find((option) => option.id === id) ?? null;
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
    const parsed = JSON.parse(raw) as UserProfile;
    if (parsed && parsed.id) {
      return findUserById(parsed.id);
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
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ id: user.id, role: user.role }));
}
