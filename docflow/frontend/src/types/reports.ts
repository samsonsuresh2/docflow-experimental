export type ReportRelationship = {
  to: string;
  via: string;
};

export type ReportMetadata = {
  entity: string;
  availableKeys: string[];
  relationships: ReportRelationship[];
};

export type DynamicReportFilter = {
  key: string;
  op: string;
  value: string;
};

export type DynamicReportJoin = {
  rightEntity: string;
  on: string;
};

export type DynamicReportRequest = {
  baseEntity: string;
  columns: string[];
  filters: DynamicReportFilter[];
  joins: DynamicReportJoin[];
};

export type ReportRunResponse = {
  columns: string[];
  rows: Array<Record<string, unknown>>;
};

export type ReportEntityList = {
  entities: string[];
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
