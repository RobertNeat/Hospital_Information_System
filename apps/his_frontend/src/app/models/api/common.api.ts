/** Spring `Page` projection. `page` is zero-based. */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Cursor-based page, newest first. `nextBefore` is the timestamp of the oldest item on this page (pass
 * as `before` to fetch the next page), or absent/`null` on the last page.
 */
export interface CursorPage<T> {
  items: T[];
  nextBefore?: string | null;
}

export type SortDirection = 'asc' | 'desc';

export interface PageQuery {
  page?: number;
  size?: number;
  /** Spring Pageable format, e.g. `'lastName,asc'`; an array is sent as repeated `sort` params. */
  sort?: string | string[];
}

export interface FieldError {
  field: string;
  message: string;
  code?: string;
}

export type ApiErrorCode =
  'NOT_FOUND' | 'VALIDATION_FAILED' | 'CONFLICT' | 'FORBIDDEN' | 'UNAUTHENTICATED' | 'INTERNAL';

/** RFC 9457 problem details, compatible with Spring's `ProblemDetail`. */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: ApiErrorCode;
  errors?: FieldError[];
}
