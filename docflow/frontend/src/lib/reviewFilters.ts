export type ReviewFilterType = 'text' | 'date' | 'dropdown';

export type ReviewFilterSource = 'DOCUMENT_PARENT' | 'META_DATA';

export interface ReviewFilterDefinition {
  key: string;
  type: ReviewFilterType;
  label: string;
  source: ReviewFilterSource;
  options?: string[];
}

export function parseReviewFilterConfig(raw: string | null | undefined): ReviewFilterDefinition[] {
  if (!raw) {
    return [];
  }

  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }

    return parsed
      .map((candidate) => normaliseDefinition(candidate))
      .filter((definition): definition is ReviewFilterDefinition => Boolean(definition && definition.key));
  } catch {
    return [];
  }
}

function normaliseDefinition(candidate: unknown): ReviewFilterDefinition | null {
  if (!candidate || typeof candidate !== 'object') {
    return null;
  }

  const obj = candidate as Record<string, unknown>;
  const key = typeof obj.key === 'string' ? obj.key.trim() : '';
  if (!key) {
    return null;
  }

  const label = typeof obj.label === 'string' && obj.label.trim() ? obj.label.trim() : key;
  const rawType = typeof obj.type === 'string' ? obj.type.trim().toLowerCase() : 'text';
  const allowedTypes: ReviewFilterType[] = ['text', 'date', 'dropdown'];
  const type = allowedTypes.includes(rawType as ReviewFilterType) ? (rawType as ReviewFilterType) : 'text';

  const rawSource = typeof obj.source === 'string' ? obj.source.trim().toUpperCase() : '';
  if (rawSource !== 'DOCUMENT_PARENT' && rawSource !== 'META_DATA') {
    return null;
  }
  const source = rawSource as ReviewFilterSource;

  const options = Array.isArray(obj.options)
    ? obj.options
        .map((option) => {
          if (option === null || option === undefined) {
            return null;
          }
          if (typeof option === 'string' || typeof option === 'number' || typeof option === 'boolean') {
            return String(option);
          }
          return null;
        })
        .filter((value): value is string => Boolean(value && value.trim()))
    : undefined;

  return { key, label, type, source, options: options && options.length > 0 ? options : undefined };
}
