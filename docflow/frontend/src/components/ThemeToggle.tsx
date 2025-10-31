import { useTheme } from '../lib/ThemeContext';

export default function ThemeToggle() {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === 'dark';

  return (
    <button
      type="button"
      onClick={toggleTheme}
      className="inline-flex items-center justify-center rounded-full border border-slate-300 bg-white/80 p-2 text-lg transition-colors hover:bg-slate-100 focus:outline-none focus:ring focus:ring-blue-200 focus:ring-offset-2 focus:ring-offset-white dark:border-slate-600 dark:bg-slate-800/80 dark:hover:bg-slate-700 dark:focus:ring-blue-500/40 dark:focus:ring-offset-slate-900"
      aria-label={`Switch to ${isDark ? 'light' : 'dark'} theme`}
      title={`Switch to ${isDark ? 'light' : 'dark'} theme`}
    >
      <span aria-hidden="true">{isDark ? '☀️' : '🌙'}</span>
    </button>
  );
}
