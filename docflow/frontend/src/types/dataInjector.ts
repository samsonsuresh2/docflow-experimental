export interface DataInjectorResponse {
  totalRows: number;
  inserted: number;
  updated: number;
  skipped: number;
  ignoredColumns: string[];
}
