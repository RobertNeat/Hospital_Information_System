import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DatePicker } from 'primeng/datepicker';
import { InputMask } from 'primeng/inputmask';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { ToggleSwitch } from 'primeng/toggleswitch';

import { FormField } from '../../../components/form-field/form-field';
import {
  GENDER_OPTIONS,
  IDENTITY_DOCUMENT_TYPE_OPTIONS,
  NO_PESEL_REASON_OPTIONS,
} from '../../../constants/labels';
import type { IdentityForm } from '../patient-registration.forms';

@Component({
  selector: 'app-registration-identity-step',
  imports: [
    FormField,
    ReactiveFormsModule,
    Select,
    DatePicker,
    ToggleSwitch,
    InputMask,
    InputText,
    Message,
    RouterLink,
  ],
  templateUrl: './identity-step.html',
  styleUrl: './identity-step.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'registration-identity-step' },
})
export class IdentityStep {
  readonly form = input.required<IdentityForm>();
  readonly duplicate = input<{ id: string; label: string } | null>(null);

  protected readonly genderOptions = GENDER_OPTIONS;
  protected readonly noPeselReasonOptions = NO_PESEL_REASON_OPTIONS;
  protected readonly identityDocumentTypeOptions = IDENTITY_DOCUMENT_TYPE_OPTIONS;
}
