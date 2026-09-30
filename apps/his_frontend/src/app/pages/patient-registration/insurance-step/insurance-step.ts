import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { ToggleSwitch } from 'primeng/toggleswitch';

import { FormField } from '../../../components/form-field/form-field';
import {
  BLOOD_TYPE_OPTIONS,
  INSURANCE_PAYER_OPTIONS,
  NFZ_BRANCH_OPTIONS,
} from '../../../constants/labels';
import type { InsuranceForm } from '../patient-registration.forms';

@Component({
  selector: 'app-registration-insurance-step',
  imports: [FormField, ReactiveFormsModule, Button, Select, ToggleSwitch, InputText, DatePipe],
  templateUrl: './insurance-step.html',
  styleUrl: './insurance-step.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'registration-insurance-step' },
})
export class InsuranceStep {
  readonly form = input.required<InsuranceForm>();
  readonly guardianRequired = input(false);
  readonly verifyEwus = output<void>();

  protected readonly insurancePayerOptions = INSURANCE_PAYER_OPTIONS;
  protected readonly nfzBranchOptions = NFZ_BRANCH_OPTIONS;
  protected readonly bloodTypeOptions = BLOOD_TYPE_OPTIONS;
}
