export type UserRole = 'ADMIN' | 'MAKER' | 'REVIEWER' | 'CHECKER';

export interface UserProfile {
  userId: string;
  role: UserRole;
}

const STORAGE_KEY = 'docflow:user';

export const USER_OPTIONS: UserProfile[] = [
  { userId: 'admin1', role: 'ADMIN' },
  { userId: 'maker1', role: 'MAKER' },
  { userId: 'reviewer1', role: 'REVIEWER' },
  { userId: 'checker1', role: 'CHECKER' },
];

export function findUserById(id: string | null | undefined): UserProfile | null {
  if (!id) {
    return null;
  }
  return USER_OPTIONS.find((option) => option.userId === id) ?? null;
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
      return findUserById(parsed.userId);
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
