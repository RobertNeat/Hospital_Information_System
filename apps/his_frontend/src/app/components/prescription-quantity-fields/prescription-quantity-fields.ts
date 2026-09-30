import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { DatePicker } from 'primeng/datepicker';
import { InputNumber } from 'primeng/inputnumber';
import { Select } from 'primeng/select';
import type { createQuantityForm } from '../../pages/prescription-wizard/prescription-wizard.helpers';
import { FormField } from '../form-field/form-field';

type QuantityForm = ReturnType<typeof createQuantityForm>;

@Component({
  selector: 'app-prescription-quantity-fields',
  imports: [ReactiveFormsModule, FormField, InputNumber, Select, DatePicker],
  templateUrl: './prescription-quantity-fields.html',
  styleUrl: './prescription-quantity-fields.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'prescription-quantity-fields' },
})
export class PrescriptionQuantityFields {
  readonly quantityForm = input.required<QuantityForm>();
  readonly datesForm = input.required<FormGroup<{ validFrom: FormControl<Date> }>>();
  readonly reimbursementOptions = input.required<{ label: string; value: string }[]>();
  readonly durationInput = output<void>();
  readonly quantityInput = output<void>();
}
