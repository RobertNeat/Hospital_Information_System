import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { DatePicker } from 'primeng/datepicker';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { SelectButton } from 'primeng/selectbutton';
import { Textarea } from 'primeng/textarea';

import { FormField } from '../../../components/form-field/form-field';
import { ADMISSION_TYPE_OPTIONS, TRIAGE_OPTIONS } from '../../../constants/labels';
import type { StaffMember, Ward } from '../../../models';
import type { AdmissionForm } from '../patient-registration.forms';

@Component({
  selector: 'app-registration-admission-step',
  imports: [FormField, ReactiveFormsModule, Select, DatePicker, InputText, SelectButton, Textarea],
  templateUrl: './admission-step.html',
  styleUrl: './admission-step.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'registration-admission-step' },
})
export class AdmissionStep {
  readonly form = input.required<AdmissionForm>();
  readonly ambulatoryOnly = input(false);
  readonly wards = input<Ward[]>([]);
  readonly doctors = input<StaffMember[]>([]);

  protected readonly admissionTypeOptions = ADMISSION_TYPE_OPTIONS;
  protected readonly triageOptions = TRIAGE_OPTIONS;
}
