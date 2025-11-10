import { DocumentStatus } from '../lib/documentStatus';

export interface DocumentResponse {
  id: number;
  documentNumber: string;
  title: string;
  status: DocumentStatus | string;
  createdBy: string;
  updatedBy: string | null;
  metadata: Record<string, unknown> | null;
  filePath: string | null;
  createdAt?: string;
  updatedAt?: string | null;
}

export interface DocumentSummary {
  id: number;
  documentNumber: string;
  title: string;
  status: DocumentStatus | string;
  createdBy: string;
  createdAt?: string;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
