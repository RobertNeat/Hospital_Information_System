import { HttpClient, HttpContext, HttpHeaders } from '@angular/common/http';
import { DestroyRef, Injectable, Injector, computed, inject, signal } from '@angular/core';
import type { Signal } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { catchError, map, of, tap, throwError } from 'rxjs';
import type { Observable } from 'rxjs';
import {
  AUTH_LOGIN_URL,
  AUTH_LOGOUT_URL,
  AUTH_ME_URL,
  AUTH_REFRESH_URL,
  AUTH_REGISTER_URL,
  AUTH_REGISTER_WARDS_URL,
} from '../config/api.config';
import { SKIP_SESSION_HANDLING } from '../config/http-context.tokens';
import { PERSIST_SESSION } from '../config/session.config';
import type {
  CurrentUser,
  LoginRequest,
  LoginResponse,
  PublicWard,
  StaffRegistrationRequest,
  StaffRegistrationResponse,
} from '../models/api';
import { PatientContextService } from './patient-context.service';

/** `setTimeout` overflows (fires immediately) above 2^31-1 ms. */
const MAX_TIMEOUT_MS = 2_147_483_647;
const EXPIRED_MESSAGE = 'Sesja wygasła. Zaloguj się ponownie.';
const STORAGE_KEY = 'his.session';
/**
 * How long before `expiresAt` to proactively call `/auth/refresh` (access tokens are short-lived,
 * see `HIS_JWT_TTL`). Kept well under the TTL so one failed attempt still leaves time for the
 * 401-triggered fallback in `errorInterceptor` before the token actually expires.
 */
const REFRESH_MARGIN_MS = 60_000;

interface StoredSession {
  accessToken: string;
  expiresAt: string;
}

/**
 * Session state for the JWT-authenticated API.
 *
 * The token/user live in a private signal; when `PERSIST_SESSION` is on (default), the
 * token and expiry are mirrored to `localStorage` so a page reload can restore the
 * session (see `restoreSession`, called from an app initializer) instead of ending it.
 * The session ends when `expiresAt` passes (timer), when the API answers 401 (see
 * `errorInterceptor`), on explicit logout, or (when persisted) when another tab logs out.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);
  /**
   * Resolved lazily so that services that merely read the session (e.g. `StaffService`)
   * can be instantiated in tests/contexts without `provideHttpClient()`.
   */
  private get http(): HttpClient {
    return this.injector.get(HttpClient);
  }
  /** Lazy to avoid a circular DI chain (`PatientContextService` -> ... -> `AuthService`). */
  private get patientContext(): PatientContextService {
    return this.injector.get(PatientContextService);
  }

  private readonly messages = inject(MessageService, { optional: true });

  private readonly _token = signal<string | null>(null);
  private readonly _user = signal<CurrentUser | null>(null);
  private readonly _expiresAt = signal<Date | null>(null);
  private expiryTimer: ReturnType<typeof setTimeout> | undefined;

  readonly currentUser: Signal<CurrentUser | null> = this._user.asReadonly();
  readonly expiresAt: Signal<Date | null> = this._expiresAt.asReadonly();
  readonly isAuthenticated: Signal<boolean> = computed(
    () => this._token() !== null && this._user() !== null,
  );
  readonly permissions: Signal<readonly string[]> = computed(() => this._user()?.permissions ?? []);

  constructor() {
    if (!PERSIST_SESSION) return;
    const onStorage = (event: StorageEvent) => {
      if (event.key === STORAGE_KEY && event.newValue === null && this.isAuthenticated()) {
        this.clearSession();
        void this.router.navigate(['/login']);
      }
    };
    window.addEventListener('storage', onStorage);
    // Without this, each test-created instance leaks a listener on the shared `window`;
    // a later test's StorageEvent then reaches a destroyed instance's injector (NG0205).
    inject(DestroyRef).onDestroy(() => window.removeEventListener('storage', onStorage));
  }

  /** Current bearer token (read by `authInterceptor`); `null` when signed out. */
  get token(): string | null {
    return this._token();
  }

  hasPermission(permission: string): boolean {
    return this.permissions().includes(permission);
  }

  login(request: LoginRequest): Observable<CurrentUser> {
    return this.http.post<LoginResponse>(AUTH_LOGIN_URL, request).pipe(
      tap((res) => this.startSession(res)),
      map((res) => res.user),
    );
  }

  register(request: StaffRegistrationRequest): Observable<StaffRegistrationResponse> {
    return this.http.post<StaffRegistrationResponse>(AUTH_REGISTER_URL, request);
  }

  /** Public ward dictionary for the registration form (no JWT needed). */
  getRegisterWards(): Observable<PublicWard[]> {
    return this.http.get<PublicWard[]>(AUTH_REGISTER_WARDS_URL);
  }

  /** Refreshes the user from `/auth/me`; emits `null` (no request) when there is no token. */
  loadCurrentUser(): Observable<CurrentUser | null> {
    if (this._token() === null) return of(null);
    return this.http.get<CurrentUser>(AUTH_ME_URL).pipe(tap((user) => this._user.set(user)));
  }

  /**
   * Calls `POST /auth/refresh` and applies the new token in place (does NOT call `clearSession`/
   * `startSession`: that would wipe `PatientContextService` on every silent renewal). Used both
   * proactively (see `scheduleExpiry`) and reactively, once, from `errorInterceptor` on a 401.
   */
  refresh(): Observable<CurrentUser> {
    const token = this._token();
    if (token === null) return throwError(() => new Error('No active session to refresh'));
    return this.http
      .post<LoginResponse>(AUTH_REFRESH_URL, null, {
        headers: new HttpHeaders({ Authorization: `Bearer ${token}` }),
      })
      .pipe(
        tap((res) => this.applyRefreshedToken(res)),
        map((res) => res.user),
      );
  }

  /** Best-effort `POST /auth/logout` (stateless), then clears the local session. Never errors. */
  logout(): Observable<void> {
    const token = this._token();
    this.clearSession();
    if (token === null) return of(undefined);
    return this.http
      .post<void>(AUTH_LOGOUT_URL, null, {
        headers: new HttpHeaders({ Authorization: `Bearer ${token}` }),
      })
      .pipe(
        map(() => undefined),
        catchError(() => of(undefined)),
      );
  }

  /** Signs out (API call is fire-and-forget) and navigates to `/login`. */
  logoutAndRedirect(): void {
    this.logout().subscribe();
    void this.router.navigate(['/login']);
  }

  /** Drops token, user and timer without calling the API (session already invalid). */
  clearSession(): void {
    clearTimeout(this.expiryTimer);
    this.expiryTimer = undefined;
    this._token.set(null);
    this._user.set(null);
    this._expiresAt.set(null);
    this.writeStoredSession(null);
    // Fix: stale patient context (sessionStorage `his.currentPatientId`) used to survive
    // logout and leak into the next session/user on the same browser.
    this.patientContext.clear();
  }

  /**
   * Restores a persisted session (if any) after a page reload: validates the stored
   * token via `/auth/me` and, on success, resumes it; otherwise clears it quietly.
   * No-op (and no request) when `PERSIST_SESSION` is off or nothing is stored.
   * Never errors/rejects (used from an app initializer, which must not block startup).
   */
  restoreSession(): Observable<void> {
    const stored = this.readStoredSession();
    if (!stored) return of(undefined);
    const expiresAt = new Date(stored.expiresAt);
    if (Number.isNaN(expiresAt.getTime()) || expiresAt.getTime() <= Date.now()) {
      this.writeStoredSession(null);
      return of(undefined);
    }
    this._token.set(stored.accessToken);
    this._expiresAt.set(expiresAt);
    return this.http
      .get<CurrentUser>(AUTH_ME_URL, {
        context: new HttpContext().set(SKIP_SESSION_HANDLING, true),
      })
      .pipe(
        tap((user) => {
          this._user.set(user);
          this.scheduleExpiry(expiresAt);
        }),
        map(() => undefined),
        catchError(() => {
          this.clearSession();
          return of(undefined);
        }),
      );
  }

  private readStoredSession(): StoredSession | null {
    if (!PERSIST_SESSION) return null;
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as Partial<StoredSession>;
      if (typeof parsed.accessToken !== 'string' || typeof parsed.expiresAt !== 'string') {
        return null;
      }
      return { accessToken: parsed.accessToken, expiresAt: parsed.expiresAt };
    } catch {
      return null;
    }
  }

  private writeStoredSession(value: StoredSession | null): void {
    if (!PERSIST_SESSION) return;
    try {
      if (value) localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
      else localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore storage errors (private mode, quota, etc.) */
    }
  }

  /** Session rejected/expired: clear it and go to `/login?returnUrl=...`. No-op when signed out. */
  expireSession(message: string = EXPIRED_MESSAGE): void {
    if (!this.isAuthenticated()) return;
    const returnUrl = this.router.url;
    this.clearSession();
    this.messages?.add({ severity: 'warn', summary: 'Sesja zakończona', detail: message });
    const hasReturn = returnUrl !== '/' && !returnUrl.startsWith('/login');
    void this.router.navigate(['/login'], hasReturn ? { queryParams: { returnUrl } } : {});
  }

  private startSession(res: LoginResponse): void {
    this.clearSession();
    if (!res.accessToken) return;
    const expiresAt = new Date(res.expiresAt);
    this._token.set(res.accessToken);
    this._user.set(res.user);
    this._expiresAt.set(expiresAt);
    this.writeStoredSession({ accessToken: res.accessToken, expiresAt: res.expiresAt });
    this.scheduleExpiry(expiresAt);
  }

  /** Applies a renewed token/expiry in place; used by `refresh()`, never by `login()`. */
  private applyRefreshedToken(res: LoginResponse): void {
    if (!res.accessToken) return;
    const expiresAt = new Date(res.expiresAt);
    this._token.set(res.accessToken);
    this._user.set(res.user);
    this._expiresAt.set(expiresAt);
    this.writeStoredSession({ accessToken: res.accessToken, expiresAt: res.expiresAt });
    clearTimeout(this.expiryTimer);
    this.scheduleExpiry(expiresAt);
  }

  /**
   * Schedules a proactive `/auth/refresh` shortly before `expiresAt` (short TTL, see
   * `REFRESH_MARGIN_MS`); the hard expiry timer remains as a fallback if the refresh call fails
   * (network issue, revoked token) and the 401 path in `errorInterceptor` does not catch it first.
   */
  private scheduleExpiry(expiresAt: Date): void {
    const untilExpiry = expiresAt.getTime() - Date.now();
    if (Number.isNaN(untilExpiry)) return;
    if (untilExpiry <= 0) {
      this.expireSession();
      return;
    }
    const untilRefresh = untilExpiry - REFRESH_MARGIN_MS;
    if (untilRefresh <= 0) {
      // Too close to expiry for a proactive refresh: fall back to the hard expiry timer.
      this.expiryTimer = setTimeout(
        () => this.expireSession(),
        Math.min(untilExpiry, MAX_TIMEOUT_MS),
      );
      return;
    }
    this.expiryTimer = setTimeout(
      () => {
        this.refresh().subscribe({
          // On failure, re-arm a short fallback timer so the hard expiry still fires on schedule.
          error: () => this.scheduleExpiry(expiresAt),
        });
      },
      Math.min(untilRefresh, MAX_TIMEOUT_MS),
    );
  }
}
