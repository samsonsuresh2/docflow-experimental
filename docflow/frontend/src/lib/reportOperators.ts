export const STRING_OPERATORS = ['EQ', 'LIKE', 'IN', 'NOT_IN'] as const;
export const NUMBER_OPERATORS = ['EQ', 'LT', 'GT', 'RANGE', 'IN', 'NOT_IN'] as const;
export const DATE_OPERATORS = ['EQ', 'LT', 'GT', 'BETWEEN'] as const;
export const REPORT_OPERATORS = ['EQ', 'LIKE', 'IN', 'NOT_IN', 'LT', 'GT', 'RANGE', 'BETWEEN'] as const;

export type ReportOperator = (typeof REPORT_OPERATORS)[number];
export type ReportLogicalType = 'STRING' | 'NUMBER' | 'DATE';

export function isRangeOperator(op: string): boolean {
  return op === 'RANGE' || op === 'BETWEEN';
}

export function isMultiValueOperator(op: string): boolean {
  return op === 'IN' || op === 'NOT_IN';
}

export function allowedOperators(type: ReportLogicalType): ReportOperator[] {
  if (type === 'STRING') {
    return [...STRING_OPERATORS];
  }
  if (type === 'NUMBER') {
    return [...NUMBER_OPERATORS];
  }
  return [...DATE_OPERATORS];
}

export function normalizeOperatorForBackend(op: string): ReportOperator {
  const upper = op.toUpperCase();
  if (upper === '=' || upper === 'EQ') return 'EQ';
  if (upper === '<' || upper === 'LT') return 'LT';
  if (upper === '>' || upper === 'GT') return 'GT';
  if (upper === 'RANGE') return 'RANGE';
  if (upper === 'BETWEEN') return 'BETWEEN';
  if (upper === 'IN') return 'IN';
  if (upper === 'NOT_IN') return 'NOT_IN';
  return 'EQ';
}
