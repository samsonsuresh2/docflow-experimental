import baseStrings from './strings.base.json';
import clientStrings from './strings.client.json';

const strings = { ...baseStrings, ...clientStrings } as const;

type TranslationKey = keyof typeof strings;

function formatMessage(template: string, values?: Record<string, string | number>): string {
  if (!values) {
    return template;
  }

  return template.replace(/\{(\w+)\}/g, (match, token) => {
    const value = values[token];
    if (value === undefined || value === null) {
      if (import.meta.env.DEV) {
        throw new Error(`Missing i18n value for "${token}" in "${template}"`);
      }
      return match;
    }
    return String(value);
  });
}

export function t(key: TranslationKey, values?: Record<string, string | number>): string {
  const template = strings[key];
  if (!template) {
    if (import.meta.env.DEV) {
      throw new Error(`Missing i18n key: ${key}`);
    }
    return String(key);
  }

  return formatMessage(template, values);
}

export type { TranslationKey };
