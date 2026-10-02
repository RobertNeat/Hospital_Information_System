import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import {
  AUTH_LOGIN_URL,
  AUTH_LOGOUT_URL,
  AUTH_ME_URL,
  AUTH_REGISTER_URL,
} from '../config/api.config';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { errorInterceptor } from '../interceptors/error.interceptor';
import type { CurrentUser, LoginResponse, StaffRegistrationRequest } from '../models/api';
import { ApiError } from '../utils/api-error';
import { AuthService } from './auth.service';

const USER: CurrentUser = {
  id: 'stf-900',
  title: 'dr',
  firstName: 'Ada',
  lastName: 'Admin',
  role: 'admin',
  wardId: 'w1',
  online: true,
  permissions: ['staff:manage'],
};

function loginResponse(expiresAt = new Date(Date.now() + 8 * 3600_000)): LoginResponse {
  return { accessToken: 'tok-123', user: USER, expiresAt: expiresAt.toISOString() };
}

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    auth.clearSession();
    vi.useRealTimers();
    http.verify();
  });

  function doLogin(res = loginResponse()) {
    auth.login({ employeeId: 'admin', password: 'admin' }).subscribe();
    http.expectOne(AUTH_LOGIN_URL).flush(res);
  }

  it('starts signed out', () => {
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.currentUser()).toBeNull();
    expect(auth.permissions()).toEqual([]);
    expect(auth.token).toBeNull();
  });

  it('login stores token, user, permissions, expiry, and persists the session', () => {
    const body = { employeeId: 'admin', password: 'admin' };
    const res = loginResponse();
    auth.login(body).subscribe();
    const req = http.expectOne(AUTH_LOGIN_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(res);
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.currentUser()?.lastName).toBe('Admin');
    expect(auth.permissions()).toEqual(['staff:manage']);
    expect(auth.hasPermission('staff:manage')).toBe(true);
    expect(auth.expiresAt()).toBeInstanceOf(Date);
    expect(auth.token).toBe('tok-123');
    expect(localStorage.getItem('his.session')).toBe(
      JSON.stringify({ accessToken: 'tok-123', expiresAt: res.expiresAt }),
    );
  });

  it.each([
    [401, 'UNAUTHENTICATED'],
    [403, 'FORBIDDEN'],
    [422, 'VALIDATION_FAILED'],
  ] as const)('login %i is surfaced as ApiError and leaves the session empty', (status, code) => {
    let error: unknown;
    auth
      .login({ employeeId: 'x', password: 'y' })
      .subscribe({ error: (e: unknown) => (error = e) });
    http
      .expectOne(AUTH_LOGIN_URL)
      .flush(
        { type: 'about:blank', title: 't', status, code, detail: 'konto zablokowane' },
        { status, statusText: 'err', headers: { 'Content-Type': 'application/problem+json' } },
      );
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(status);
    expect((error as ApiError).code).toBe(code);
    expect(auth.isAuthenticated()).toBe(false);
    // A failed login must not trigger the global 401 redirect.
    expect(navigate).not.toHaveBeenCalled();
  });

  it('loadCurrentUser does nothing without a token', () => {
    let result: unknown = 'unset';
    auth.loadCurrentUser().subscribe((u) => (result = u));
    http.expectNone(AUTH_ME_URL);
    expect(result).toBeNull();
  });

  it('loadCurrentUser calls /auth/me with the bearer token and updates the user', () => {
    doLogin();
    auth.loadCurrentUser().subscribe();
    const req = http.expectOne(AUTH_ME_URL);
    expect(req.request.headers.get('Authorization')).toBe('Bearer tok-123');
    req.flush({ ...USER, firstName: 'Zmieniony' });
    expect(auth.currentUser()?.firstName).toBe('Zmieniony');
  });

  it('logout clears state immediately, drops the persisted session, and calls POST /auth/logout with the old token', () => {
    doLogin();
    expect(localStorage.getItem('his.session')).not.toBeNull();
    let done = false;
    auth.logout().subscribe(() => (done = true));
    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('his.session')).toBeNull();
    const req = http.expectOne(AUTH_LOGOUT_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Authorization')).toBe('Bearer tok-123');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(done).toBe(true);
  });

  it('logout is best-effort: an API failure still completes', () => {
    doLogin();
    let done = false;
    auth.logout().subscribe(() => (done = true));
    http.expectOne(AUTH_LOGOUT_URL).flush(null, { status: 500, statusText: 'err' });
    expect(done).toBe(true);
    expect(auth.token).toBeNull();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('logout clears the stale patient context (sessionStorage) so it does not leak to the next session', () => {
    doLogin();
    sessionStorage.setItem('his.currentPatientId', 'pat-001');
    auth.logout().subscribe();
    http.expectOne(AUTH_LOGOUT_URL).flush(null, { status: 204, statusText: 'No Content' });
    expect(sessionStorage.getItem('his.currentPatientId')).toBeNull();
  });

  it('logoutAndRedirect clears the session and goes to /login', () => {
    doLogin();
    auth.logoutAndRedirect();
    http.expectOne(AUTH_LOGOUT_URL).flush(null, { status: 204, statusText: 'No Content' });
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('logs out automatically at expiresAt', () => {
    vi.useFakeTimers();
    doLogin(loginResponse(new Date(Date.now() + 60_000)));
    expect(auth.isAuthenticated()).toBe(true);
    vi.advanceTimersByTime(59_000);
    expect(auth.isAuthenticated()).toBe(true);
    vi.advanceTimersByTime(2_000);
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.token).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], {});
  });

  it('survives TTLs beyond the setTimeout range', () => {
    vi.useFakeTimers();
    const max = 2_147_483_647;
    doLogin(loginResponse(new Date(Date.now() + max + 5_000)));
    vi.advanceTimersByTime(max);
    expect(auth.isAuthenticated()).toBe(true);
    vi.advanceTimersByTime(6_000);
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('does not keep a session whose expiresAt is already in the past', () => {
    doLogin(loginResponse(new Date(Date.now() - 1_000)));
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('a storage event reporting removal of the session key in another tab signs this tab out', () => {
    doLogin();
    window.dispatchEvent(
      new StorageEvent('storage', { key: 'his.session', newValue: null, oldValue: '{}' }),
    );
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  describe('restoreSession', () => {
    it('does nothing when nothing is stored', () => {
      let done = false;
      auth.restoreSession().subscribe(() => (done = true));
      http.expectNone(AUTH_ME_URL);
      expect(done).toBe(true);
      expect(auth.isAuthenticated()).toBe(false);
    });

    it('validates a stored session via /auth/me and resumes it', () => {
      const res = loginResponse();
      localStorage.setItem(
        'his.session',
        JSON.stringify({ accessToken: res.accessToken, expiresAt: res.expiresAt }),
      );
      let done = false;
      auth.restoreSession().subscribe(() => (done = true));
      const req = http.expectOne(AUTH_ME_URL);
      expect(req.request.headers.get('Authorization')).toBe('Bearer tok-123');
      req.flush(USER);
      expect(done).toBe(true);
      expect(auth.isAuthenticated()).toBe(true);
      expect(auth.currentUser()?.lastName).toBe('Admin');
    });

    it('drops an already-expired stored session without calling /auth/me', () => {
      localStorage.setItem(
        'his.session',
        JSON.stringify({
          accessToken: 'tok-123',
          expiresAt: new Date(Date.now() - 1_000).toISOString(),
        }),
      );
      let done = false;
      auth.restoreSession().subscribe(() => (done = true));
      http.expectNone(AUTH_ME_URL);
      expect(done).toBe(true);
      expect(auth.isAuthenticated()).toBe(false);
      expect(localStorage.getItem('his.session')).toBeNull();
    });

    it('clears the session quietly (no toast/navigate) when /auth/me rejects the stored token', () => {
      const res = loginResponse();
      localStorage.setItem(
        'his.session',
        JSON.stringify({ accessToken: res.accessToken, expiresAt: res.expiresAt }),
      );
      let done = false;
      auth.restoreSession().subscribe(() => (done = true));
      http.expectOne(AUTH_ME_URL).flush(
        { type: 'about:blank', title: 't', status: 401, code: 'UNAUTHENTICATED' },
        {
          status: 401,
          statusText: 'err',
          headers: { 'Content-Type': 'application/problem+json' },
        },
      );
      expect(done).toBe(true);
      expect(auth.isAuthenticated()).toBe(false);
      expect(auth.token).toBeNull();
      expect(localStorage.getItem('his.session')).toBeNull();
      expect(navigate).not.toHaveBeenCalled();
    });

    it('ignores a malformed stored value', () => {
      localStorage.setItem('his.session', 'not-json');
      let done = false;
      auth.restoreSession().subscribe(() => (done = true));
      http.expectNone(AUTH_ME_URL);
      expect(done).toBe(true);
      expect(auth.isAuthenticated()).toBe(false);
    });
  });

  const REGISTRATION = { employeeId: 'new-1', password: 'abcdefgh' } as StaffRegistrationRequest;

  it('register 201 returns a pending account without a token', () => {
    let status: string | undefined;
    auth.register(REGISTRATION).subscribe((r) => (status = r.accountStatus));
    const req = http.expectOne(AUTH_REGISTER_URL);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(
      { staffId: 'stf-77', accountStatus: 'pending' },
      { status: 201, statusText: 'Created' },
    );
    expect(status).toBe('pending');
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('register 409 is surfaced as ApiError CONFLICT', () => {
    let error: unknown;
    auth.register(REGISTRATION).subscribe({ error: (e: unknown) => (error = e) });
    http
      .expectOne(AUTH_REGISTER_URL)
      .flush(
        { title: 'dup', status: 409, code: 'CONFLICT' },
        { status: 409, statusText: 'Conflict' },
      );
    expect((error as ApiError).code).toBe('CONFLICT');
  });
});
