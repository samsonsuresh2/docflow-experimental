import type {
  ReportExecutionRunRequest,
  ReportMailApiConfig,
  ReportMailAttachmentFormat,
  ReportMailConfig,
  ReportMailFieldConfig,
  ReportMailFieldName,
  ReportMailMode,
  ReportMailRuntimeValues,
  ReportMailSendDraft,
} from '../types/reports';

export function createDefaultReportMailFieldConfig(): ReportMailFieldConfig {
  return {
    mandatory: '',
    default: '',
    editable: true,
  };
}

export function createDefaultReportMailConfig(): ReportMailConfig {
  return {
    enabled: false,
    mode: 'INLINE_ONLY',
    attachmentFormat: 'CSV',
    fields: {
      to: createDefaultReportMailFieldConfig(),
      cc: createDefaultReportMailFieldConfig(),
      subject: createDefaultReportMailFieldConfig(),
      body: createDefaultReportMailFieldConfig(),
      disclaimer: createDefaultReportMailFieldConfig(),
    },
  };
}

export function normalizeReportMailConfig(value?: Partial<ReportMailConfig> | ReportMailApiConfig | null): ReportMailConfig {
  const defaults = createDefaultReportMailConfig();
  if (!value) {
    return defaults;
  }

  const sourceFields = 'fields' in value && value.fields ? value.fields : null;
  const normalizedFields = { ...defaults.fields };
  (Object.keys(normalizedFields) as ReportMailFieldName[]).forEach((fieldName) => {
    const sourceField = sourceFields ? sourceFields[fieldName] : value[fieldName];
    normalizedFields[fieldName] = {
      mandatory: typeof sourceField?.mandatory === 'string' ? sourceField.mandatory : '',
      default: typeof sourceField?.default === 'string' ? sourceField.default : '',
      editable: sourceField?.editable !== false,
    };
  });

  return {
    enabled: Boolean(value.enabled),
    mode: normalizeReportMailMode(value.mode),
    attachmentFormat: normalizeReportMailAttachmentFormat(value.attachmentFormat),
    fields: normalizedFields,
  };
}

export function toReportMailApiConfig(config: ReportMailConfig): ReportMailApiConfig {
  return {
    enabled: config.enabled,
    mode: config.mode,
    attachmentFormat: config.attachmentFormat,
    to: { ...config.fields.to },
    cc: { ...config.fields.cc },
    subject: { ...config.fields.subject },
    body: { ...config.fields.body },
    disclaimer: { ...config.fields.disclaimer },
  };
}

export function normalizeReportMailMode(value?: string | null): ReportMailMode {
  if (value === 'ATTACHMENT_ONLY' || value === 'INLINE_OR_ATTACHMENT') {
    return value;
  }
  return 'INLINE_ONLY';
}

export function normalizeReportMailAttachmentFormat(value?: string | null): ReportMailAttachmentFormat {
  return value === 'EXCEL' ? 'EXCEL' : 'CSV';
}

export function splitEmailList(value: string | null | undefined): string[] {
  if (!value) {
    return [];
  }
  const seen = new Set<string>();
  const addresses: string[] = [];
  value
    .split(/[\n,;]+/)
    .map((part) => part.trim())
    .filter(Boolean)
    .forEach((address) => {
      const key = address.toLowerCase();
      if (seen.has(key)) {
        return;
      }
      seen.add(key);
      addresses.push(address);
    });
  return addresses;
}

export function joinEmailList(value: string[] | null | undefined): string {
  return (value ?? []).join(', ');
}

export function buildRuntimeMailValues(
  config: ReportMailConfig,
  overrides: Partial<ReportMailRuntimeValues>,
): ReportMailRuntimeValues {
  return {
    to: overrides.to ?? config.fields.to.default,
    cc: overrides.cc ?? config.fields.cc.default,
    subject: overrides.subject ?? config.fields.subject.default,
    body: overrides.body ?? config.fields.body.default,
    disclaimer: overrides.disclaimer ?? config.fields.disclaimer.default,
  };
}

export function buildReportFilterSummary(request: ReportExecutionRunRequest | null | undefined): string {
  if (!request || !request.filters || request.filters.length === 0) {
    return 'No filters applied';
  }
  const parts = request.filters.map((filter) => {
    const key = filter.key ?? 'filter';
    if (filter.mode === 'PRESET' && filter.presetCode) {
      return `${key} = ${filter.presetCode}`;
    }
    if (filter.valueFrom || filter.valueTo) {
      return `${key} ${filter.op ?? ''} ${filter.valueFrom ?? ''}${filter.valueTo ? `..${filter.valueTo}` : ''}`.trim();
    }
    if (filter.value) {
      return `${key} ${filter.op ?? ''} ${filter.value}`.trim();
    }
    return key;
  });
  return parts.join(' | ');
}

export function buildReportMailSendDraft(
  templateId: number,
  filterSummary: string,
  deliveryMode: 'INLINE' | 'ATTACHMENT',
  fields: ReportMailRuntimeValues,
): ReportMailSendDraft {
  return {
    templateId,
    filterSummary,
    deliveryMode,
    fields,
  };
}
