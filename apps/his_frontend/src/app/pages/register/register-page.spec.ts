import { beforeEach, describe, expect, it, vi } from 'vitest';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { AUTH_REGISTER_URL, AUTH_REGISTER_WARDS_URL } from '../../config/api.config';
import { authInterceptor } from '../../interceptors/auth.interceptor';
import { errorInterceptor } from '../../interceptors/error.interceptor';
import { RegisterPage } from './register-page';

type Internals = {
  form: {
    valid: boolean;
    patchValue(v: object): void;
    hasError(k: string): boolean;
    get(name: string): { errors: Record<string, unknown> | null } | null;
  };
  errorMessage(): string | null;
  loading(): boolean;
  submit(): void;
};

const VALID = {
  firstName: 'Jan',
  lastName: 'Kowalski',
  title: 'lek.',
  specialization: 'Kardiologia',
  pwz: '1234567',
  wardId: 'w1',
  employeeId: 'HIS-000123',
  password: 'abcdefgh',
  passwordConfirm: 'abcdefgh',
  acceptTerms: true,
};

describe('RegisterPage', () => {
  let http: HttpTestingController;
  let messages: MessageService;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterPage],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    messages = TestBed.inject(MessageService);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  const page = () =>
    TestBed.createComponent(RegisterPage).componentInstance as unknown as Internals;

  it('loads wards from the public endpoint without a token', () => {
    const fixture = TestBed.createComponent(RegisterPage);
    const req = http.expectOne(AUTH_REGISTER_WARDS_URL);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([{ id: 'w1', name: 'Interna', shortName: 'INT' }]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('#register-ward-error')).toBeNull();
    expect((fixture.componentInstance as unknown as { wards(): unknown[] }).wards()).toHaveLength(
      1,
    );
  });

  it('shows a message and an empty select when wards cannot be loaded', () => {
    const fixture = TestBed.createComponent(RegisterPage);
    http.expectOne(AUTH_REGISTER_WARDS_URL).flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('#register-ward-error')).not.toBeNull();
    expect((fixture.componentInstance as unknown as { wards(): unknown[] }).wards()).toEqual([]);
    http.expectNone(AUTH_REGISTER_WARDS_URL);
  });

  it('flags mismatching passwords', () => {
    const p = page();
    p.form.patchValue({ password: 'abcdefgh', passwordConfirm: 'abcdefgx' });
    expect(p.form.hasError('passwordMismatch')).toBe(true);
  });

  it('does not call the API when invalid', () => {
    page().submit();
    http.expectNone(AUTH_REGISTER_URL);
  });

  it('registers (201), announces pending activation and returns to login', () => {
    const p = page();
    const add = vi.spyOn(messages, 'add');
    p.form.patchValue(VALID);
    p.submit();
    const req = http.expectOne(AUTH_REGISTER_URL);
    expect(req.request.body.employeeId).toBe('HIS-000123');
    expect(req.request.body.passwordConfirm).toBeUndefined();
    req.flush({ staffId: 's1', accountStatus: 'pending' }, { status: 201, statusText: 'Created' });
    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({
        detail: expect.stringContaining('oczekuje na aktywację przez administratora'),
      }),
    );
    expect(navigate).toHaveBeenCalledWith(['/login']);
    expect(p.loading()).toBe(false);
  });

  it('shows a duplicate message on 409', () => {
    const p = page();
    p.form.patchValue(VALID);
    p.submit();
    http
      .expectOne(AUTH_REGISTER_URL)
      .flush(
        { code: 'CONFLICT', detail: 'Identyfikator jest zajęty.' },
        { status: 409, statusText: 'Conflict' },
      );
    expect(p.errorMessage()).toBe('Identyfikator jest zajęty.');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('maps 422 field errors onto the form controls', () => {
    const p = page();
    p.form.patchValue(VALID);
    p.submit();
    http
      .expectOne(AUTH_REGISTER_URL)
      .flush(
        { code: 'VALIDATION_FAILED', errors: [{ field: 'pwz', message: 'Nieprawidłowy PWZ.' }] },
        { status: 422, statusText: 'Unprocessable' },
      );
    expect(p.form.get('pwz')?.errors).toEqual({ server: 'Nieprawidłowy PWZ.' });
    expect(p.errorMessage()).toBe('Popraw zaznaczone pola formularza.');
  });
});
