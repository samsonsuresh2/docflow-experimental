import type {
  DynamicReportRequest,
  ReportEntityList,
  ReportMetadata,
  ReportRunResponse,
  ReportTemplate,
  ReportTemplateList,
} from '../types/reports';
import client from './api';

export async function fetchReportEntities(): Promise<string[]> {
  const response = await client.get<ReportEntityList>('/reports/meta');
  return response.data.entities ?? [];
}

export async function fetchReportMetadata(entity: string): Promise<ReportMetadata> {
  const response = await client.get<ReportMetadata>('/reports/meta', {
    params: { entity },
  });
  return response.data;
}

export async function fetchReportTemplates(): Promise<ReportTemplate[]> {
  const response = await client.get<ReportTemplateList>('/reports/templates');
  return response.data.templates ?? [];
}

export async function saveReportTemplate(
  name: string,
  request: DynamicReportRequest,
): Promise<ReportTemplate> {
  const response = await client.post<ReportTemplate>('/reports/templates', {
    name,
    request,
  });
  return response.data;
}

export async function runDynamicReport(
  request: DynamicReportRequest,
  page: number,
  size: number,
): Promise<ReportRunResponse> {
  const response = await client.post<ReportRunResponse>(
    '/reports/run',
    request,
    {
      params: {
        page,
        size,
      },
    },
  );
  return response.data;
}
