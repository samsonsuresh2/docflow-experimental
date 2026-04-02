import { describe, expect, it } from 'vitest';
import {
  buildReportFilterSummary,
  createDefaultReportMailConfig,
  normalizeReportMailConfig,
  splitEmailList,
  toReportMailApiConfig,
} from './reportMail';

describe('report mail helpers', () => {
  it('creates a stable default config', () => {
    const config = createDefaultReportMailConfig();

    expect(config.enabled).toBe(false);
    expect(config.mode).toBe('INLINE_ONLY');
    expect(config.attachmentFormat).toBe('CSV');
    expect(config.fields.to.default).toBe('');
    expect(config.fields.subject.editable).toBe(true);
  });

  it('normalizes flat api config safely', () => {
    const config = normalizeReportMailConfig({
      enabled: true,
      mode: 'INLINE_OR_ATTACHMENT',
      attachmentFormat: 'EXCEL',
      to: { mandatory: 'ops@company.internal', default: 'ops@company.internal', editable: false },
    });

    expect(config.enabled).toBe(true);
    expect(config.mode).toBe('INLINE_OR_ATTACHMENT');
    expect(config.attachmentFormat).toBe('EXCEL');
    expect(config.fields.to.mandatory).toBe('ops@company.internal');
    expect(config.fields.to.editable).toBe(false);
    expect(config.fields.cc.default).toBe('');
  });

  it('converts ui config to api config', () => {
    const apiConfig = toReportMailApiConfig({
      enabled: true,
      mode: 'INLINE_ONLY',
      attachmentFormat: 'CSV',
      fields: {
        to: { mandatory: 'a@company.internal', default: 'b@company.internal', editable: true },
        cc: { mandatory: '', default: '', editable: true },
        subject: { mandatory: 'Mandatory', default: 'Default', editable: true },
        body: { mandatory: '', default: '', editable: true },
        disclaimer: { mandatory: '', default: '', editable: true },
      },
    });

    expect(apiConfig.to?.mandatory).toBe('a@company.internal');
    expect(apiConfig.subject?.default).toBe('Default');
  });

  it('splits and deduplicates addresses', () => {
    expect(splitEmailList('a@company.internal, b@company.internal; a@company.internal\nc@company.internal')).toEqual([
      'a@company.internal',
      'b@company.internal',
      'c@company.internal',
    ]);
  });

  it('summarizes report filters', () => {
    expect(
      buildReportFilterSummary({
        templateId: 1,
        filters: [
          { key: 'status', op: 'EQ', value: 'APPROVED' },
          { key: 'createdAt', op: 'BETWEEN', valueFrom: '2026-03-01', valueTo: '2026-03-31' },
        ],
      }),
    ).toContain('status EQ APPROVED');
  });
});
