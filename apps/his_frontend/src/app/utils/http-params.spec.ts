import { describe, expect, it } from 'vitest';
import { toHttpParams } from './http-params';

describe('toHttpParams', () => {
  it('skips undefined, null and empty values but keeps 0 and false', () => {
    const p = toHttpParams({ a: undefined, b: null, c: '', d: 0, e: false, f: 'x' });
    expect(p.keys().sort()).toEqual(['d', 'e', 'f']);
    expect(p.get('d')).toBe('0');
    expect(p.get('e')).toBe('false');
  });

  it('serialises arrays as repeated parameters, dropping blanks', () => {
    const p = toHttpParams({ sort: ['lastName,asc', '', 'id,desc', undefined] });
    expect(p.getAll('sort')).toEqual(['lastName,asc', 'id,desc']);
  });

  it('formats dates as ISO-8601 UTC and handles paging', () => {
    const p = toHttpParams({ page: 2, size: 50, orderedFrom: new Date('2026-01-31T10:15:00Z') });
    expect(p.get('orderedFrom')).toBe('2026-01-31T10:15:00.000Z');
    expect(p.get('page')).toBe('2');
    expect(p.get('size')).toBe('50');
  });

  it('accepts an empty or missing query', () => {
    expect(toHttpParams().keys()).toEqual([]);
    expect(toHttpParams(null).keys()).toEqual([]);
  });
});
