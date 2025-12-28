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

export type ExecutableReportFilterType = 'TEXT' | 'NUMBER' | 'DATE';

export type ExecutableReportFilterField = {
  key: string;
  label: string;
  type: ExecutableReportFilterType;
  allowedOps: ('=' | '<' | '>')[];
  dateFormat?: string | null;
};

export type ExecutableReportTemplateDetail = {
  templateId: number;
  name: string;
  filters: ExecutableReportFilterField[];
};

export type ReportExecutionFilterInput = {
  key: string;
  op: string;
  value: string;
};

export type ReportExecutionRunRequest = {
  templateId: number;
  filters: ReportExecutionFilterInput[];
};
