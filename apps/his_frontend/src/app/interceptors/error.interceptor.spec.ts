import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AUTH_LOGIN_URL, AUTH_REFRESH_URL } from '../config/api.config';
import type { CurrentUser, LoginResponse } from '../models/api';
import { AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';
import { errorInterceptor } from './error.interceptor';

const USER: CurrentUser = {
  id: 'stf-900',
  title: 'dr',
  firstName: 'Ada',
  lastName: 'Admin',
  role: 'admin',
  wardId: 'w1',
  online: true,
  permissions: [],
};

function loginResponse(token = 'tok-123'): LoginResponse {
  return {
    accessToken: token,
    user: USER,
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
  };
}

/**
 * The 401 -> refresh -> retry fallback in `errorInterceptor`. `AuthService`'s own specs cover the
 * proactive (timer-based) refresh path; this covers the reactive path triggered by a 401 response.
 */
describe('errorInterceptor (401 -> refresh -> retry)', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let client: HttpClient;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    auth.login({ employeeId: 'admin', password: 'admin' }).subscribe();
    http.expectOne(AUTH_LOGIN_URL).flush(loginResponse());
  });

  afterEach(() => {
    auth.clearSession();
    http.verify();
  });

  it('retries the original request with the refreshed token after a 401', () => {
    let result: unknown;
    client.get('/api/v1/staff').subscribe((r) => (result = r));

    const first = http.expectOne('/api/v1/staff');
    first.flush(null, { status: 401, statusText: 'err' });

    http.expectOne(AUTH_REFRESH_URL).flush(loginResponse('tok-456'));

    const retried = http.expectOne('/api/v1/staff');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer tok-456');
    retried.flush({ ok: true });

    expect(result).toEqual({ ok: true });
    expect(navigate).not.toHaveBeenCalled();
    expect(auth.token).toBe('tok-456');
  });

  it('ends the session when the refresh itself fails', () => {
    let error: unknown;
    client.get('/api/v1/staff').subscribe({ error: (e: unknown) => (error = e) });

    http.expectOne('/api/v1/staff').flush(null, { status: 401, statusText: 'err' });
    http.expectOne(AUTH_REFRESH_URL).flush(null, { status: 401, statusText: 'err' });

    expect(error).toBeDefined();
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], {});
  });

  it('shares a single refresh call across concurrent 401s', () => {
    client.get('/api/v1/staff').subscribe();
    client.get('/api/v1/wards').subscribe();

    http.expectOne('/api/v1/staff').flush(null, { status: 401, statusText: 'err' });
    http.expectOne('/api/v1/wards').flush(null, { status: 401, statusText: 'err' });

    http.expectOne(AUTH_REFRESH_URL).flush(loginResponse('tok-789'));

    http.expectOne('/api/v1/staff').flush({ ok: true });
    http.expectOne('/api/v1/wards').flush({ ok: true });
  });

  it('does not retry a request that was already retried once (no infinite loop)', () => {
    let error: unknown;
    client.get('/api/v1/staff').subscribe({ error: (e: unknown) => (error = e) });

    http.expectOne('/api/v1/staff').flush(null, { status: 401, statusText: 'err' });
    http.expectOne(AUTH_REFRESH_URL).flush(loginResponse('tok-456'));
    http.expectOne('/api/v1/staff').flush(null, { status: 401, statusText: 'err' });

    expect(error).toBeDefined();
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], {});
  });
});
