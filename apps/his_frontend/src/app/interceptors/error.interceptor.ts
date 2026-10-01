import { HttpErrorResponse } from '@angular/common/http';
import type { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL, AUTH_LOGOUT_URL } from '../config/api.config';
import { AuthService } from '../services/auth.service';
import { toApiError } from '../utils/api-error';
import { isPublicAuthUrl } from './auth.interceptor';

/**
 * Maps `HttpErrorResponse` of `/api/` calls to `ApiError` (ProblemDetail) and, on a 401
 * outside login/register/logout, ends the session and redirects to `/login?returnUrl=`.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(`${API_BASE_URL}/`)) return next(req);
  const auth = inject(AuthService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) return throwError(() => error);
      const sessionCall = isPublicAuthUrl(req.url) || req.url.startsWith(AUTH_LOGOUT_URL);
      if (error.status === 401 && !sessionCall) {
        auth.expireSession('Sesja wygasła lub jest nieprawidłowa. Zaloguj się ponownie.');
      }
      return throwError(() => toApiError(error));
    }),
  );
};
