import type {
  DynamicReportRequest,
  ReportAdminScope,
  ReportRunResponse,
  ReportTemplate,
  ReportTemplateList,
} from '../types/reports';
import client from './api';

export async function fetchReportScope(baseEntity?: string): Promise<ReportAdminScope> {
  const response = await client.get<ReportAdminScope>('/reports/admin/scope', {
    params: baseEntity ? { baseEntity } : undefined,
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
