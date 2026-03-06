export type ReportBaseEntity = {
  name: string;
  label: string;
  type: string;
  joinsToDocument: boolean;
  businessFkColumn?: string | null;
};

export type ReportAdminScope = {
  entities: ReportBaseEntity[];
  baseColumns: string[];
  documentColumns: string[];
  metadataKeys: string[];
};

export type DynamicReportFilter = {
  key: string;
  op: string;
  value: string;
  mode?: 'FIXED_VALUE' | 'USER_INPUT';
  label?: string;
  dataType?: string;
  source?: 'DOCUMENT' | 'DOCUMENT_METADATA' | 'THIRD_PARTY_ENTITY';
  field?: string;
  logicalType?: 'STRING' | 'NUMBER' | 'DATE';
  allowedOperators?: ('EQ' | 'LIKE' | 'LT' | 'GT')[];
};

export type DynamicReportRequest = {
  baseEntity: string;
  columns: string[];
  filters: DynamicReportFilter[];
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
  allowedOps: ('EQ' | 'LIKE' | 'LT' | 'GT')[];
  dateFormat?: string | null;
  presetEnabled?: boolean;
  presets?: { code: string; name: string; displayOrder: number }[];
};

export type ExecutableReportTemplateDetail = {
  templateId: number;
  name: string;
  filters: ExecutableReportFilterField[];
};

export type ReportExecutionFilterInput = {
  key: string;
  op?: string;
  value?: string;
  mode?: 'MANUAL' | 'PRESET';
  fromValue?: string;
  toValue?: string;
  presetCode?: string;
};

export type ReportExecutionRunRequest = {
  templateId: number;
  filters: ReportExecutionFilterInput[];
};
