import { EMPTY, expand, forkJoin, map, of, reduce, switchMap } from 'rxjs';
import type { Observable } from 'rxjs';
import type { CursorPage, Page } from '../models/api';

/** Backend maximum page size. */
export const MAX_PAGE_SIZE = 100;

/** Reads every page of a paged endpoint (first page first, the rest in parallel) into one array. */
export function readAllPages<T>(fetchPage: (page: number) => Observable<Page<T>>): Observable<T[]> {
  return fetchPage(0).pipe(
    switchMap((first) => {
      if (first.totalPages <= 1) return of(first.items);
      const rest = Array.from({ length: first.totalPages - 1 }, (_, i) => fetchPage(i + 1));
      return forkJoin(rest).pipe(map((pages) => [first, ...pages].flatMap((p) => p.items)));
    }),
  );
}

/**
 * Reads every page of a cursor-paginated endpoint (newest first, see `CursorPage`), sequentially
 * following `nextBefore`, into one array (still newest first).
 */
export function readAllCursorPages<T>(
  fetchPage: (before: string | undefined) => Observable<CursorPage<T>>,
): Observable<T[]> {
  return fetchPage(undefined).pipe(
    expand((page) => (page.nextBefore ? fetchPage(page.nextBefore) : EMPTY)),
    reduce<CursorPage<T>, T[]>((acc, page) => [...acc, ...page.items], []),
  );
}
