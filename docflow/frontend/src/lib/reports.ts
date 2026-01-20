import type {
  DynamicReportRequest,
  ExecutableReportTemplateDetail,
  ExecutableReportTemplateSummary,
  ReportAdminScope,
  ReportExecutionRunRequest,
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

export async function fetchExecutableReportTemplates(): Promise<ExecutableReportTemplateSummary[]> {
  const response = await client.get<{ templates: ExecutableReportTemplateSummary[] }>('/reports/templates', {
    params: { mode: 'exec' },
  });
  return response.data.templates ?? [];
}

export async function fetchExecutableReportTemplate(templateId: number): Promise<ExecutableReportTemplateDetail> {
  const response = await client.get<ExecutableReportTemplateDetail>(`/reports/templates/${templateId}`);
  return response.data;
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

export async function updateReportTemplate(
  templateId: number,
  name: string,
  request: DynamicReportRequest,
): Promise<ReportTemplate> {
  const response = await client.put<ReportTemplate>(`/reports/templates/${templateId}`, {
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

export async function runReportTemplate(
  request: ReportExecutionRunRequest,
  page: number,
  size: number,
): Promise<ReportRunResponse> {
  const response = await client.post<ReportRunResponse>(
    '/reports/run',
    request,
    {
      params: {
        mode: 'exec',
        page,
        size,
      },
    },
  );
  return response.data;
}
