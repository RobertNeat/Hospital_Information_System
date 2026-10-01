import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { InputText } from 'primeng/inputtext';
import { Password } from 'primeng/password';
import { Select } from 'primeng/select';

import { FormField } from '../../components/form-field/form-field';
import { ACADEMIC_TITLE_OPTIONS, SPECIALIZATION_OPTIONS } from '../../constants/auth-options';
import { STAFF_ROLE_OPTIONS } from '../../constants/labels';
import { registerErrorMessage, REGISTER_PENDING_MESSAGE } from '../../constants/auth-messages';
import { AuthService } from '../../services/auth.service';
import { WardService } from '../../services/ward.service';
import { passwordMatchValidator } from '../../validators/password-match.validator';
import { phoneValidator } from '../../validators/phone.validator';
import { employeeIdValidator, pwzValidator } from '../../validators/staff-identifiers.validator';
import type { StaffRole } from '../../models';
import { toApiError } from '../../utils/api-error';
import { toRequest } from './register.mappers';

@Component({
  selector: 'app-register-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonDirective,
    Checkbox,
    InputText,
    Password,
    Select,
    FormField,
  ],
  templateUrl: './register-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'register-page' },
})
export class RegisterPage {
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);
  private readonly auth = inject(AuthService);

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly roleOptions = STAFF_ROLE_OPTIONS;
  protected readonly titleOptions = ACADEMIC_TITLE_OPTIONS;
  protected readonly specializationOptions = SPECIALIZATION_OPTIONS;
  protected readonly wards = toSignal(inject(WardService).getWards(), { initialValue: [] });

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      role: ['doctor' as StaffRole, Validators.required],
      title: ['', Validators.required],
      specialization: ['', Validators.required],
      pwz: ['', [Validators.required, pwzValidator()]],
      wardId: ['', Validators.required],
      phone: ['', phoneValidator()],
      employeeId: ['', [Validators.required, employeeIdValidator()]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      passwordConfirm: ['', Validators.required],
      acceptTerms: [false, Validators.requiredTrue],
    },
    { validators: passwordMatchValidator('password', 'passwordConfirm') },
  );

  protected submit(): void {
    if (this.loading()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    this.auth.register(toRequest(this.form.getRawValue())).subscribe({
      next: () => {
        this.loading.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Wniosek wysłany',
          detail: REGISTER_PENDING_MESSAGE,
          life: 8000,
        });
        void this.router.navigate(['/login']);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const error = toApiError(err);
        this.errorMessage.set(registerErrorMessage(error));
        this.applyFieldErrors(error.fieldErrors);
      },
    });
  }

  /** Shows API field errors (422) under the matching controls. */
  private applyFieldErrors(errors: { field: string; message: string }[]): void {
    for (const { field, message } of errors) {
      const control = this.form.get(field);
      if (control) {
        control.setErrors({ server: message });
        control.markAsTouched();
      }
    }
  }
}
