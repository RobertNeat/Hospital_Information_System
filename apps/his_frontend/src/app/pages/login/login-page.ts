import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Password } from 'primeng/password';

import { FormField } from '../../components/form-field/form-field';
import { employeeIdValidator } from '../../validators/staff-identifiers.validator';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, ButtonDirective, InputText, Password, FormField],
  templateUrl: './login-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'login-page' },
})
export class LoginPage {
  private readonly router = inject(Router);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    employeeId: ['', [Validators.required, employeeIdValidator()]],
    password: ['', Validators.required],
  });

  /** Demo only: no real authentication, a valid form just enters the app. */
  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    void this.router.navigate(['/dashboard']);
  }
}
