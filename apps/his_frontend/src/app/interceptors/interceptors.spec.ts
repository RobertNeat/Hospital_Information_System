import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AUTH_LOGIN_URL, AUTH_REGISTER_URL } from '../config/api.config';
import { AuthService } from '../services/auth.service';
import { ApiError } from '../utils/api-error';
import { authInterceptor } from './auth.interceptor';
import { errorInterceptor } from './error.interceptor';

describe('HTTP interceptors', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let auth: AuthService;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/patients/pat-1/orders');
    navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    auth.clearSession();
    ctrl.verify();
  });

  function signIn() {
    auth.login({ employeeId: 'admin', password: 'admin' }).subscribe();
    ctrl.expectOne(AUTH_LOGIN_URL).flush({
      accessToken: 'tok-1',
      user: {
        id: 'u1',
        title: '',
        firstName: 'A',
        lastName: 'B',
        role: 'admin',
        wardId: 'w',
        online: true,
      },
      expiresAt: new Date(Date.now() + 3600_000).toISOString(),
    });
  }

  it('adds the bearer token to /api/ requests', () => {
    signIn();
    http.get('/api/v1/patients').subscribe();
    const req = ctrl.expectOne('/api/v1/patients');
    expect(req.request.headers.get('Authorization')).toBe('Bearer tok-1');
    req.flush([]);
  });

  it('does not add a header without a token', () => {
    http.get('/api/v1/patients').subscribe();
    const req = ctrl.expectOne('/api/v1/patients');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('does not add the token to login or register', () => {
    signIn();
    http.post(AUTH_LOGIN_URL, {}).subscribe();
    http.post(AUTH_REGISTER_URL, {}).subscribe();
    const login = ctrl.expectOne(AUTH_LOGIN_URL);
    const register = ctrl.expectOne(AUTH_REGISTER_URL);
    expect(login.request.headers.has('Authorization')).toBe(false);
    expect(register.request.headers.has('Authorization')).toBe(false);
    login.flush({});
    register.flush({});
  });

  it('does not add the token to non-API requests', () => {
    signIn();
    http.get('/assets/logo.png').subscribe();
    const req = ctrl.expectOne('/assets/logo.png');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush('');
  });

  it('401 on an API call clears the session and redirects to /login with returnUrl', () => {
    signIn();
    let error: unknown;
    http.get('/api/v1/patients').subscribe({ error: (e: unknown) => (error = e) });
    ctrl
      .expectOne('/api/v1/patients')
      .flush(
        { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'UNAUTHENTICATED' },
        { status: 401, statusText: 'Unauthorized' },
      );
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/patients/pat-1/orders' },
    });
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(401);
  });

  it('parallel 401s redirect only once', () => {
    signIn();
    http.get('/api/v1/a').subscribe({ error: () => undefined });
    http.get('/api/v1/b').subscribe({ error: () => undefined });
    for (const url of ['/api/v1/a', '/api/v1/b']) {
      ctrl.expectOne(url).flush({}, { status: 401, statusText: 'Unauthorized' });
    }
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('401 from login does not redirect (wrong credentials)', () => {
    let error: unknown;
    http.post(AUTH_LOGIN_URL, {}).subscribe({ error: (e: unknown) => (error = e) });
    ctrl.expectOne(AUTH_LOGIN_URL).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(navigate).not.toHaveBeenCalled();
    expect((error as ApiError).code).toBe('UNAUTHENTICATED');
  });

  it('maps a problem+json body to ApiError with field errors', () => {
    let error: ApiError | undefined;
    http.post('/api/v1/x', {}).subscribe({ error: (e: ApiError) => (error = e) });
    ctrl.expectOne('/api/v1/x').flush(
      {
        type: 'urn:his:validation',
        title: 'Validation failed',
        status: 422,
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'pwz', message: 'Zły PWZ' }],
      },
      { status: 422, statusText: 'Unprocessable' },
    );
    expect(error?.fieldErrors).toEqual([{ field: 'pwz', message: 'Zły PWZ' }]);
    expect(error?.problem.title).toBe('Validation failed');
  });

  it('maps a network failure to status 0', () => {
    let error: ApiError | undefined;
    http.get('/api/v1/x').subscribe({ error: (e: ApiError) => (error = e) });
    ctrl.expectOne('/api/v1/x').error(new ProgressEvent('error'));
    expect(error?.status).toBe(0);
  });
});
