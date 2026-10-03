import { HttpContextToken } from '@angular/common/http';

/**
 * Opt-out for callers with their own 401 recovery (currently `AuthService.restoreSession`, which
 * already falls back to a quiet `clearSession()` on a rejected stored token): tells
 * `errorInterceptor` to skip both the refresh-retry and the `expireSession()` redirect/toast for
 * this request's 401s. Lives in its own module (not `error.interceptor.ts`) so `AuthService` can
 * reference it without an import cycle (the interceptor already depends on `AuthService`).
 */
export const SKIP_SESSION_HANDLING = new HttpContextToken<boolean>(() => false);
