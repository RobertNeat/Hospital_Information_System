import { HttpErrorResponse } from '@angular/common/http';
import type { ApiErrorCode, FieldError, ProblemDetail } from '../models/api';

const STATUS_CODES: Record<number, ApiErrorCode> = {
  401: 'UNAUTHENTICATED',
  403: 'FORBIDDEN',
  404: 'NOT_FOUND',
  409: 'CONFLICT',
  422: 'VALIDATION_FAILED',
};

/** HTTP failure normalised to the `ProblemDetail` contract (status 0 = network error). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: ProblemDetail,
  ) {
    super(problem.detail ?? problem.title);
    this.name = 'ApiError';
  }

  get code(): ApiErrorCode | undefined {
    return this.problem.code;
  }

  get fieldErrors(): FieldError[] {
    return this.problem.errors ?? [];
  }
}

function asProblem(body: unknown): Partial<ProblemDetail> {
  return typeof body === 'object' && body !== null ? (body as Partial<ProblemDetail>) : {};
}

/** Maps any thrown value (usually `HttpErrorResponse`) to an `ApiError`. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (error instanceof HttpErrorResponse) {
    const p = asProblem(error.error);
    const status = error.status;
    return new ApiError(status, {
      type: p.type ?? 'about:blank',
      title: p.title ?? (status === 0 ? 'Brak połączenia z serwerem' : error.statusText),
      status: p.status ?? status,
      detail: p.detail,
      instance: p.instance,
      code: p.code ?? STATUS_CODES[status] ?? (status >= 500 ? 'INTERNAL' : undefined),
      errors: p.errors,
    });
  }
  const message = error instanceof Error ? error.message : 'Nieoczekiwany błąd';
  return new ApiError(0, { type: 'about:blank', title: message, status: 0, code: 'INTERNAL' });
}
