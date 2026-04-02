import type {
  DynamicReportRequest,
  ExecutableReportTemplateDetail,
  ExecutableReportTemplateSummary,
  ReportAdminScope,
  ReportExecutionRunRequest,
  ReportRunResponse,
  ReportTemplate,
  ReportTemplateList,
  ReportMailSendRequest,
} from '../types/reports';
import client from './api';

export async function fetchReportScope(baseEntity?: string): Promise<ReportAdminScope> {
  const response = await client.get<ReportAdminScope>('/reports/admin/scope', {
    params: baseEntity ? { baseEntity } : undefined,
  });
  return response.data;
}

export async function fetchAllReportRows(
  fetchPage: (page: number, size: number) => Promise<ReportRunResponse>,
  pageSize = 500,
): Promise<ReportRunResponse> {
  const firstPage = await fetchPage(0, pageSize);
  const totalRows = firstPage.rowCount ?? firstPage.rows.length;
  if (totalRows <= firstPage.rows.length) {
    return {
      columns: firstPage.columns,
      rows: firstPage.rows,
      rowCount: totalRows,
    };
  }

  const allRows = [...firstPage.rows];
  const totalPages = Math.max(1, Math.ceil(totalRows / pageSize));
  for (let page = 1; page < totalPages; page += 1) {
    const nextPage = await fetchPage(page, pageSize);
    allRows.push(...nextPage.rows);
  }

  return {
    columns: firstPage.columns,
    rows: allRows,
    rowCount: totalRows,
  };
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
  const response = await client.get<ExecutableReportTemplateDetail>(`/reports/templates/${templateId}`, {
    params: { mode: 'exec' },
  });
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

export async function sendReportMail(request: ReportMailSendRequest): Promise<void> {
  await client.post('/reports/mail', request, {
    params: { mode: 'exec' },
  });
}
