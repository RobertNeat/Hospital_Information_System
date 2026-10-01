import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Password } from 'primeng/password';

import { FormField } from '../../components/form-field/form-field';
import { loginErrorMessage } from '../../constants/auth-messages';
import { AuthService } from '../../services/auth.service';
import { toApiError } from '../../utils/api-error';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, ButtonDirective, InputText, Password, FormField],
  templateUrl: './login-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'login-page' },
})
export class LoginPage {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  // Login only requires non-empty values: the server is the authority on the credentials,
  // so demo accounts (e.g. `user`, `lab-tech`) must never be rejected by a client regex.
  protected readonly form = inject(FormBuilder).nonNullable.group({
    employeeId: ['', Validators.required],
    password: ['', Validators.required],
  });

  protected submit(): void {
    if (this.loading()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    const { employeeId, password } = this.form.getRawValue();
    this.auth.login({ employeeId: employeeId.trim(), password }).subscribe({
      next: () => {
        this.loading.set(false);
        void this.router.navigateByUrl(this.returnUrl());
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(loginErrorMessage(toApiError(err)));
      },
    });
  }

  /** Only same-app absolute paths are honoured (no open redirect). */
  private returnUrl(): string {
    const url = this.route.snapshot.queryParamMap.get('returnUrl');
    return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login')
      ? url
      : '/dashboard';
  }
}
