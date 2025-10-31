export type Theme = 'light' | 'dark';

const THEME_STORAGE_KEY = 'docflow-theme';

const isBrowser = typeof window !== 'undefined';

export function getStoredTheme(): Theme | null {
  if (!isBrowser) {
    return null;
  }
  const value = window.localStorage.getItem(THEME_STORAGE_KEY);
  return value === 'light' || value === 'dark' ? value : null;
}

export function saveTheme(theme: Theme) {
  if (!isBrowser) {
    return;
  }
  window.localStorage.setItem(THEME_STORAGE_KEY, theme);
}

export function getSystemTheme(): Theme {
  if (!isBrowser) {
    return 'light';
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function applyTheme(theme: Theme) {
  if (!isBrowser) {
    return;
  }
  const root = window.document.documentElement;
  root.classList.toggle('dark', theme === 'dark');
  root.dataset.theme = theme;
  root.style.colorScheme = theme;
}

export function initializeTheme(): Theme {
  if (!isBrowser) {
    return 'light';
  }
  const storedTheme = getStoredTheme();
  const themeToApply = storedTheme ?? getSystemTheme();
  applyTheme(themeToApply);
  return themeToApply;
}
