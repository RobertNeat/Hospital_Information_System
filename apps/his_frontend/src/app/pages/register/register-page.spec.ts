import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { RegisterPage } from './register-page';

type Internals = {
  form: {
    valid: boolean;
    patchValue(v: object): void;
    hasError(k: string): boolean;
  };
  submit(): void;
};

describe('RegisterPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterPage],
      providers: [provideRouter([]), MessageService],
    }).compileComponents();
  });

  it('flags mismatching passwords', () => {
    const page = TestBed.createComponent(RegisterPage).componentInstance as unknown as Internals;
    page.form.patchValue({ password: 'abcdefgh', passwordConfirm: 'abcdefgx' });
    expect(page.form.hasError('passwordMismatch')).toBe(true);
  });

  it('submits a valid form and returns to login', () => {
    const page = TestBed.createComponent(RegisterPage).componentInstance as unknown as Internals;
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    page.form.patchValue({
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
    });
    page.submit();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
