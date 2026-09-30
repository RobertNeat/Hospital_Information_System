import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
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
import { WardService } from '../../services/ward.service';
import { passwordMatchValidator } from '../../validators/password-match.validator';
import { phoneValidator } from '../../validators/phone.validator';
import { employeeIdValidator, pwzValidator } from '../../validators/staff-identifiers.validator';
import type { StaffRole } from '../../models';
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

  /** Demo only: no account is created, a valid form returns to the login page. */
  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    // Request is built for the future API call; the demo does not send it.
    void toRequest(this.form.getRawValue());
    this.messageService.add({
      severity: 'success',
      summary: 'Wniosek wysłany',
      detail: 'Konto zostanie aktywowane po weryfikacji przez administratora.',
    });
    void this.router.navigate(['/login']);
  }
}
