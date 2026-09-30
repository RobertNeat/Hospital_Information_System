import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { Checkbox } from 'primeng/checkbox';
import { Message } from 'primeng/message';
import { SelectButton } from 'primeng/selectbutton';
import type { CreatinineEgfr } from '../../pages/imaging-order-wizard/imaging-order-wizard.helpers';
import type { Step3Form } from '../../pages/imaging-order-wizard/imaging-order-wizard.forms';
import { FormField } from '../form-field/form-field';

@Component({
  selector: 'app-imaging-safety-form',
  imports: [ReactiveFormsModule, Checkbox, Message, SelectButton, FormField, DatePipe],
  templateUrl: './imaging-safety-form.html',
  styleUrl: './imaging-safety-form.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'imaging-safety-form' },
})
export class ImagingSafetyForm {
  readonly form = input.required<Step3Form>();
  readonly needsPregnancyCheck = input.required<boolean>();
  readonly isMri = input.required<boolean>();
  readonly egfrBlocksContrast = input.required<boolean>();
  readonly latestCreatinineEgfr = input.required<CreatinineEgfr | null>();
}
