import { t } from '../i18n';

export default function NotAuthorized({ message }: { message?: string }) {
  return (
    <div className="rounded border border-amber-200 bg-amber-50 p-6 text-sm text-amber-800 shadow-sm transition-colors dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-100">
      {message ?? t('common.notAuthorized')}
    </div>
  );
}
