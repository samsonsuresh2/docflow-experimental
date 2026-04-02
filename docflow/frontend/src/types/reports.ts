export type ReportBaseEntity = {
  name: string;
  label: string;
  type: string;
  joinsToDocument: boolean;
  businessFkColumn?: string | null;
};

export const REPORT_MAIL_FIELDS = ['to', 'cc', 'subject', 'body', 'disclaimer'] as const;
export type ReportMailFieldName = (typeof REPORT_MAIL_FIELDS)[number];
export type ReportMailMode = 'INLINE_ONLY' | 'ATTACHMENT_ONLY' | 'INLINE_OR_ATTACHMENT';
export type ReportMailDeliveryMode = 'INLINE' | 'ATTACHMENT';
export type ReportMailAttachmentFormat = 'CSV' | 'EXCEL';

export type ReportMailFieldConfig = {
  mandatory: string;
  default: string;
  editable: boolean;
};

export type ReportMailConfig = {
  enabled: boolean;
  mode: ReportMailMode;
  attachmentFormat: ReportMailAttachmentFormat;
  fields: Record<ReportMailFieldName, ReportMailFieldConfig>;
};

export type ReportMailApiConfig = {
  enabled?: boolean;
  mode?: ReportMailMode;
  attachmentFormat?: ReportMailAttachmentFormat;
  to?: Partial<ReportMailFieldConfig> | null;
  cc?: Partial<ReportMailFieldConfig> | null;
  subject?: Partial<ReportMailFieldConfig> | null;
  body?: Partial<ReportMailFieldConfig> | null;
  disclaimer?: Partial<ReportMailFieldConfig> | null;
};

export type ReportMailRuntimeValues = Record<ReportMailFieldName, string>;

export type ReportMailSendRequest = {
  templateId: number;
  filters: ReportExecutionFilterInput[];
  filterSummary: string;
  deliveryMode: ReportMailDeliveryMode;
  fields: ReportMailRuntimeValues;
};

export type ReportMailSendDraft = Omit<ReportMailSendRequest, 'filters'>;

export type ReportAdminScope = {
  entities: ReportBaseEntity[];
  baseColumns: string[];
  documentColumns: string[];
  metadataKeys: string[];
  presets: { code: string; name: string; displayOrder: number }[];
};

export type DynamicReportFilter = {
  key: string;
  op: string;
  value?: string;
  valueFrom?: string;
  valueTo?: string;
  mode?: 'FIXED_VALUE' | 'USER_INPUT';
  label?: string;
  dataType?: string;
  source?: 'DOCUMENT' | 'DOCUMENT_METADATA' | 'THIRD_PARTY_ENTITY';
  field?: string;
  logicalType?: 'STRING' | 'NUMBER' | 'DATE';
  allowedOperators?: ('EQ' | 'LIKE' | 'LT' | 'GT' | 'RANGE' | 'BETWEEN')[];
  presetCodes?: string[];
};

export type DynamicReportRequest = {
  baseEntity: string;
  columns: string[];
  filters: DynamicReportFilter[];
  mail?: ReportMailApiConfig | null;
};

export type ReportRunResponse = {
  columns: string[];
  rows: Array<Record<string, unknown>>;
  rowCount?: number;
};

export type ReportTemplate = {
  id: number;
  name: string;
  request: DynamicReportRequest;
  createdBy: string;
  createdAt: string;
  description?: string | null;
};

export type ReportTemplateList = {
  templates: ReportTemplate[];
};

export type ExecutableReportTemplateSummary = {
  id: number;
  name: string;
  description?: string | null;
};

export type ExecutableReportFilterType = 'STRING' | 'NUMBER' | 'DATE';

export type ExecutableReportFilterField = {
  key: string;
  label: string;
  type: ExecutableReportFilterType;
  allowedOps: ('EQ' | 'LIKE' | 'LT' | 'GT' | 'RANGE' | 'BETWEEN')[];
  dateFormat?: string | null;
  presetEnabled?: boolean;
  presets?: { code: string; name: string; displayOrder: number }[];
};

export type ExecutableReportTemplateDetail = {
  templateId: number;
  name: string;
  filters: ExecutableReportFilterField[];
  mail?: ReportMailApiConfig | null;
};

export type ReportExecutionFilterInput = {
  key: string;
  op?: string;
  value?: string;
  valueFrom?: string;
  valueTo?: string;
  mode?: 'MANUAL' | 'PRESET';
  fromValue?: string;
  toValue?: string;
  presetCode?: string;
};

export type ReportExecutionRunRequest = {
  templateId: number;
  filters: ReportExecutionFilterInput[];
};
