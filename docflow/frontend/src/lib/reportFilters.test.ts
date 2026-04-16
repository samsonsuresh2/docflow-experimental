import { describe, expect, it } from 'vitest';
import { parseNumberMultiValueInput, parseStringMultiValueInput } from './reportFilterValues';
import { allowedOperators } from './reportOperators';

describe('report filter operator helpers', () => {
  it('exposes IN and NOT_IN for string filters', () => {
    expect(allowedOperators('STRING')).toEqual(['EQ', 'LIKE', 'IN', 'NOT_IN']);
  });

  it('exposes IN and NOT_IN for number filters while keeping date unchanged', () => {
    expect(allowedOperators('NUMBER')).toEqual(['EQ', 'LT', 'GT', 'RANGE', 'IN', 'NOT_IN']);
    expect(allowedOperators('DATE')).toEqual(['EQ', 'LT', 'GT', 'BETWEEN']);
  });
});

describe('report multi-value parsing', () => {
  it('parses escaped commas for string IN inputs', () => {
    expect(parseStringMultiValueInput('A\\,B, C , D')).toEqual(['A,B', 'C', 'D']);
  });

  it('rejects malformed string escape sequences', () => {
    expect(() => parseStringMultiValueInput('A\\x,B')).toThrow();
  });

  it('parses numeric comma-separated values', () => {
    expect(parseNumberMultiValueInput('1000, 2000, 5000')).toEqual(['1000', '2000', '5000']);
  });

  it('rejects non-numeric entries in numeric lists', () => {
    expect(() => parseNumberMultiValueInput('1000, nope')).toThrow();
  });
});
