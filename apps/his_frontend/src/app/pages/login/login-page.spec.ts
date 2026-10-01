import { beforeEach, describe, expect, it, vi } from 'vitest';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { AUTH_LOGIN_URL } from '../../config/api.config';
import { authInterceptor } from '../../interceptors/auth.interceptor';
import { errorInterceptor } from '../../interceptors/error.interceptor';
import { LoginPage } from './login-page';

type Internals = {
  form: {
    setValue(v: object): void;
    controls: { employeeId: { valid: boolean } };
  };
  loading(): boolean;
  errorMessage(): string | null;
  submit(): void;
};

const USER = {
  id: 'u',
  title: '',
  firstName: 'A',
  lastName: 'B',
  role: 'admin',
  wardId: 'w',
  online: true,
};

describe('LoginPage', () => {
  let http: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;
  let returnUrl: string | null;

  beforeEach(async () => {
    returnUrl = null;
    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              get queryParamMap() {
                return convertToParamMap(returnUrl ? { returnUrl } : {});
              },
            },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  const ok = () => ({
    accessToken: 't',
    user: USER,
    expiresAt: new Date(Date.now() + 3600_000).toISOString(),
  });

  function page(): Internals {
    return TestBed.createComponent(LoginPage).componentInstance as unknown as Internals;
  }

  function submit(p: Internals) {
    p.form.setValue({ employeeId: 'admin', password: 'admin' });
    p.submit();
  }

  function fail(status: number, body: object) {
    const p = page();
    submit(p);
    http.expectOne(AUTH_LOGIN_URL).flush(body, { status, statusText: 'err' });
    return p;
  }

  it('does not call the API with an empty form', () => {
    page().submit();
    http.expectNone(AUTH_LOGIN_URL);
  });

  it.each(['user', 'lab-tech', 'admin', 'EMP-0001'])('accepts demo login "%s"', (id) => {
    const p = page();
    p.form.setValue({ employeeId: id, password: 'x' });
    expect(p.form.controls.employeeId.valid).toBe(true);
  });

  it('logs in and navigates to the dashboard', () => {
    const p = page();
    submit(p);
    expect(p.loading()).toBe(true);
    http.expectOne(AUTH_LOGIN_URL).flush(ok());
    expect(p.loading()).toBe(false);
    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('honours a safe returnUrl', () => {
    returnUrl = '/patients/pat-1/orders';
    submit(page());
    http.expectOne(AUTH_LOGIN_URL).flush(ok());
    expect(navigateByUrl).toHaveBeenCalledWith('/patients/pat-1/orders');
  });

  it.each(['//evil.example', 'https://evil.example', '/login'])(
    'ignores unsafe returnUrl %s',
    (url) => {
      returnUrl = url;
      submit(page());
      http.expectOne(AUTH_LOGIN_URL).flush(ok());
      expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
    },
  );

  it('shows the Polish message for 401', () => {
    const p = fail(401, { code: 'UNAUTHENTICATED' });
    expect(p.errorMessage()).toBe('Nieprawidłowy login lub hasło.');
    expect(p.loading()).toBe(false);
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('shows the API detail for 403', () => {
    const p = fail(403, { code: 'FORBIDDEN', detail: 'Konto oczekuje na aktywację.' });
    expect(p.errorMessage()).toBe('Konto oczekuje na aktywację.');
  });

  it('shows field messages for 422', () => {
    const p = fail(422, {
      code: 'VALIDATION_FAILED',
      errors: [{ field: 'employeeId', message: 'Wymagane.' }],
    });
    expect(p.errorMessage()).toBe('Wymagane.');
  });

  it('renders the error in the template', () => {
    const fixture = TestBed.createComponent(LoginPage);
    submit(fixture.componentInstance as unknown as Internals);
    http
      .expectOne(AUTH_LOGIN_URL)
      .flush({ code: 'UNAUTHENTICATED' }, { status: 401, statusText: 'x' });
    fixture.detectChanges();
    const el = (fixture.nativeElement as HTMLElement).querySelector('#login-error');
    expect(el?.textContent).toContain('Nieprawidłowy login lub hasło.');
  });
});
