import { HttpContext, HttpContextToken, HttpErrorResponse } from '@angular/common/http';
import type { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, Subject, catchError, switchMap, take, throwError } from 'rxjs';
import { API_BASE_URL, AUTH_LOGOUT_URL, AUTH_REFRESH_URL } from '../config/api.config';
import { SKIP_SESSION_HANDLING } from '../config/http-context.tokens';
import { AuthService } from '../services/auth.service';
import { toApiError } from '../utils/api-error';
import { isPublicAuthUrl } from './auth.interceptor';

/** Marks a request as already retried once after a refresh, to prevent infinite retry loops. */
const RETRIED_AFTER_REFRESH = new HttpContextToken<boolean>(() => false);

/** `null` while idle; an `Observable` shared by concurrent 401s while a refresh is in flight. */
let refreshInFlight: Observable<string> | null = null;

function isSessionExemptUrl(url: string): boolean {
  return (
    isPublicAuthUrl(url) || url.startsWith(AUTH_LOGOUT_URL) || url.startsWith(AUTH_REFRESH_URL)
  );
}

/** Single-flight `/auth/refresh`: concurrent 401s share one call instead of each firing their own. */
function sharedRefresh(auth: AuthService): Observable<string> {
  if (!refreshInFlight) {
    const subject = new Subject<string>();
    refreshInFlight = subject.asObservable();
    auth.refresh().subscribe({
      next: () => {
        refreshInFlight = null;
        if (auth.token) subject.next(auth.token);
        else subject.error(new Error('Refresh produced no token'));
        subject.complete();
      },
      error: (err: unknown) => {
        refreshInFlight = null;
        subject.error(err);
      },
    });
  }
  return refreshInFlight;
}

/**
 * Maps `HttpErrorResponse` of `/api/` calls to `ApiError` (ProblemDetail). On a 401 outside
 * login/register/logout/refresh, attempts one `/auth/refresh` (shared across concurrent 401s) and
 * retries the original request with the new token; if the refresh also fails (or the request was
 * already retried once), ends the session and redirects to `/login?returnUrl=`.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(`${API_BASE_URL}/`)) return next(req);
  const auth = inject(AuthService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) return throwError(() => error);
      const sessionCall = isSessionExemptUrl(req.url) || req.context.get(SKIP_SESSION_HANDLING);
      const alreadyRetried = req.context.get(RETRIED_AFTER_REFRESH);
      if (error.status === 401 && !sessionCall && !alreadyRetried && auth.token) {
        return sharedRefresh(auth).pipe(
          take(1),
          switchMap((newToken) => {
            const retried = req.clone({
              setHeaders: { Authorization: `Bearer ${newToken}` },
              context: new HttpContext().set(RETRIED_AFTER_REFRESH, true),
            });
            return next(retried);
          }),
          catchError(() => {
            auth.expireSession('Sesja wygasła lub jest nieprawidłowa. Zaloguj się ponownie.');
            return throwError(() => toApiError(error));
          }),
        );
      }
      if (error.status === 401 && !sessionCall) {
        auth.expireSession('Sesja wygasła lub jest nieprawidłowa. Zaloguj się ponownie.');
      }
      return throwError(() => toApiError(error));
    }),
  );
};
