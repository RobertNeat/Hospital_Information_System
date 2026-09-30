import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { LoginPage } from './login-page';

describe('LoginPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('does not navigate with an empty form', () => {
    const fixture = TestBed.createComponent(LoginPage);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    (fixture.componentInstance as unknown as { submit(): void }).submit();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('navigates to the dashboard with valid credentials', () => {
    const fixture = TestBed.createComponent(LoginPage);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const page = fixture.componentInstance as unknown as {
      form: { setValue(v: object): void };
      submit(): void;
    };
    page.form.setValue({ employeeId: 'HIS-000123', password: 'secret' });
    page.submit();
    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
  });
});
