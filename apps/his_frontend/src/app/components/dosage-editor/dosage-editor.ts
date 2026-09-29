import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule, type FormGroup } from '@angular/forms';
import { startWith, switchMap } from 'rxjs';
import { Checkbox } from 'primeng/checkbox';
import { InputNumber } from 'primeng/inputnumber';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { FormField } from '../form-field/form-field';
import { FREQUENCY_OPTIONS, ROUTE_LABELS, TIME_OF_DAY_OPTIONS } from '../../constants/labels';
import type { Drug, DoseFrequency } from '../../models';
import { computeDailyDose, exceedsMaxDailyDose } from './dosage-math';

interface DosageFormValue {
  dose: number | null;
  frequency: DoseFrequency | null;
  asNeeded: boolean;
  maxPerDay: number | null;
}

@Component({
  selector: 'app-dosage-editor',
  imports: [
    ReactiveFormsModule,
    InputNumber,
    Select,
    ToggleSwitch,
    Checkbox,
    Textarea,
    Message,
    FormField,
    DecimalPipe,
  ],
  templateUrl: './dosage-editor.html',
  styleUrl: './dosage-editor.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'dosage-editor' },
})
export class DosageEditor {
  readonly form = input.required<FormGroup>();
  readonly drug = input.required<Drug>();

  protected readonly routeOptions = computed(() =>
    this.drug().routes.map((r) => ({ value: r, label: ROUTE_LABELS[r] })),
  );
  protected readonly frequencyOptions = FREQUENCY_OPTIONS;
  protected readonly timeOfDayOptions = TIME_OF_DAY_OPTIONS;

  // A plain `computed()` over `form().value` will not re-render under zoneless+OnPush when the
  // form is passed in as an input and mutated internally by user typing (same trap `FormField`
  // documents for `markAllAsTouched`) -- so we subscribe to `valueChanges` directly, the same
  // pattern as `form-field.ts`, using `getRawValue()` so disabled controls (e.g. `maxPerDay`
  // when PRN is off) are still included when relevant.
  private readonly formValue = toSignal(
    toObservable(this.form).pipe(switchMap((f) => f.valueChanges.pipe(startWith(f.getRawValue())))),
    { initialValue: null },
  );

  protected readonly dailyDose = computed(() => {
    const v = this.formValue() as DosageFormValue | null;
    if (!v) return null;
    return computeDailyDose({
      dose: v.dose,
      doseUnit: this.drug().defaultDoseUnit,
      frequency: v.frequency,
      asNeeded: v.asNeeded,
      maxPerDay: v.maxPerDay,
    });
  });

  protected readonly exceedsMax = computed(() =>
    exceedsMaxDailyDose(this.dailyDose(), this.drug()),
  );

  protected readonly doseUnit = computed(() => this.drug().defaultDoseUnit);
}
