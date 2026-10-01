import { HttpParams } from '@angular/common/http';

export type QueryValue = string | number | boolean | Date | null | undefined;
export type QueryObject = Record<string, QueryValue | readonly QueryValue[]>;

const toWire = (v: string | number | boolean | Date): string =>
  v instanceof Date ? v.toISOString() : String(v);

/**
 * Builds `HttpParams` from a query object. Skips undefined/null/empty-string values;
 * arrays become a repeated parameter (Spring `sort=a,asc&sort=b,desc`); Dates are ISO-8601 UTC.
 * `false` and `0` are kept.
 */
export function toHttpParams(query: QueryObject | null | undefined = {}): HttpParams {
  let params = new HttpParams();
  for (const [key, raw] of Object.entries(query ?? {})) {
    const values: readonly QueryValue[] = Array.isArray(raw)
      ? (raw as readonly QueryValue[])
      : [raw as QueryValue];
    for (const v of values) {
      if (v !== undefined && v !== null && v !== '') params = params.append(key, toWire(v));
    }
  }
  return params;
}
