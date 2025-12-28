export type ModuleCode =
  | 'UPLOAD'
  | 'REVIEW'
  | 'APPROVAL'
  | 'REPORTS'
  | 'REPORT_CONFIG'
  | 'AUDIT'
  | 'DATA_INGEST'
  | 'ADMIN';

export const ModuleCodes: Record<ModuleCode, ModuleCode> = {
  UPLOAD: 'UPLOAD',
  REVIEW: 'REVIEW',
  APPROVAL: 'APPROVAL',
  REPORTS: 'REPORTS',
  REPORT_CONFIG: 'REPORT_CONFIG',
  AUDIT: 'AUDIT',
  DATA_INGEST: 'DATA_INGEST',
  ADMIN: 'ADMIN',
};
