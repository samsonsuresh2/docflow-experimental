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
};

export type ReportTemplate = {
  id: number;
  name: string;
  request: DynamicReportRequest;
  createdBy: string;
  createdAt: string;
};

export type ReportTemplateList = {
  templates: ReportTemplate[];
};
