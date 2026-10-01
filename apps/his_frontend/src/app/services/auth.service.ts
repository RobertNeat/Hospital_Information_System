import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, Injector, computed, inject, signal } from '@angular/core';
import type { Signal } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { catchError, map, of, tap } from 'rxjs';
import type { Observable } from 'rxjs';
import {
  AUTH_LOGIN_URL,
  AUTH_LOGOUT_URL,
  AUTH_ME_URL,
  AUTH_REGISTER_URL,
} from '../config/api.config';
import type {
  CurrentUser,
  LoginRequest,
  LoginResponse,
  StaffRegistrationRequest,
  StaffRegistrationResponse,
} from '../models/api';

/** `setTimeout` overflows (fires immediately) above 2^31-1 ms. */
const MAX_TIMEOUT_MS = 2_147_483_647;
const EXPIRED_MESSAGE = 'Sesja wygasła. Zaloguj się ponownie.';

/**
 * Session state for the JWT-authenticated API.
 *
 * The access token lives ONLY in memory (a private signal): it is not written to
 * localStorage/sessionStorage, so a page reload ends the session (the user logs in
 * again). The session also ends when `expiresAt` passes (timer) or when the API
 * answers 401 (see `errorInterceptor`).
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

  /** Refreshes the user from `/auth/me`; emits `null` (no request) when there is no token. */
  loadCurrentUser(): Observable<CurrentUser | null> {
    if (this._token() === null) return of(null);
    return this.http.get<CurrentUser>(AUTH_ME_URL).pipe(tap((user) => this._user.set(user)));
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
    this.scheduleExpiry(expiresAt);
  }

  private scheduleExpiry(expiresAt: Date): void {
    const delay = expiresAt.getTime() - Date.now();
    if (Number.isNaN(delay)) return;
    if (delay <= 0) {
      this.expireSession();
      return;
    }
    this.expiryTimer = setTimeout(
      () => this.scheduleExpiry(expiresAt),
      Math.min(delay, MAX_TIMEOUT_MS),
    );
  }
}
