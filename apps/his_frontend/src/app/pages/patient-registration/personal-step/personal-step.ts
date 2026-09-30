import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { InputText } from 'primeng/inputtext';

import { FormField } from '../../../components/form-field/form-field';
import type { PersonalForm } from '../patient-registration.forms';

@Component({
  selector: 'app-registration-personal-step',
  imports: [FormField, ReactiveFormsModule, InputText],
  templateUrl: './personal-step.html',
  styleUrl: './personal-step.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'registration-personal-step' },
})
export class PersonalStep {
  readonly form = input.required<PersonalForm>();
}
