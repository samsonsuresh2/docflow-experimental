import { DocumentStatus } from '../lib/documentStatus';

export interface DocumentResponse {
  id: number;
  documentNumber: string;
  title: string;
  status: DocumentStatus | string;
  createdBy: string;
  updatedBy: string | null;
  metadata: Record<string, unknown> | null;
}
