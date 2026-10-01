import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import type { AbstractControl } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { Select } from 'primeng/select';
import { Tag } from 'primeng/tag';
import { Textarea } from 'primeng/textarea';
import { VITAL_CONTEXT_OPTIONS } from '../../constants/labels';
import type { FieldError, VitalsRecordResponse, VitalType } from '../../models';
import { VitalsService } from '../../services/vitals.service';
import { toApiError } from '../../utils/api-error';
import { classifyVital, type VitalClassification } from '../../utils/vitals-anomaly';
import { FormField } from '../form-field/form-field';

/** Vital fields the entry form edits directly (excludes `painScore`, which has its own 0-10 scale). */
const NUMERIC_FIELDS: VitalType[] = [
  'systolic',
  'diastolic',
  'heartRate',
  'temperature',
  'spo2',
  'respiratoryRate',
];

export type VitalsSaveResult = VitalsRecordResponse;

@Component({
  selector: 'app-vitals-entry-form',
  imports: [ReactiveFormsModule, InputNumber, Select, Textarea, Tag, Button, FormField],
  templateUrl: './vitals-entry-form.html',
  styleUrl: './vitals-entry-form.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'vitals-entry-form' },
})
export class VitalsEntryForm {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly vitalsService = inject(VitalsService);
  private readonly messageService = inject(MessageService);

  readonly patientId = input.required<string>();
  readonly saved = output<VitalsSaveResult>();

  protected readonly contextOptions = VITAL_CONTEXT_OPTIONS;
  /** Backend thresholds (input bounds and the live hint); empty until loaded. */
  protected readonly thresholds = this.vitalsService.thresholds;
  protected readonly saving = signal(false);

  protected readonly form = this.fb.group({
    systolic: this.fb.control<number | null>(null),
    diastolic: this.fb.control<number | null>(null),
    heartRate: this.fb.control<number | null>(null),
    temperature: this.fb.control<number | null>(null),
    spo2: this.fb.control<number | null>(null),
    respiratoryRate: this.fb.control<number | null>(null),
    painScore: this.fb.control<number | null>(null, [Validators.min(0), Validators.max(10)]),
    context: this.fb.control<'office_exam' | 'ward_round' | 'triage' | 'observation'>(
      'ward_round',
      [Validators.required],
    ),
    notes: this.fb.control(''),
  });

  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.value });

  /** Live per-field hint against the backend thresholds (the saved anomalies come from the server). */
  protected readonly liveAnomalies = computed<Partial<Record<VitalType, VitalClassification>>>(
    () => {
      const v = this.formValue();
      const thresholds = this.thresholds();
      const map: Partial<Record<VitalType, VitalClassification>> = {};
      for (const field of NUMERIC_FIELDS) {
        const c = classifyVital(thresholds, field, v[field]);
        if (c) map[field] = c;
      }
      return map;
    },
  );

  constructor() {
    this.vitalsService.loadThresholds().subscribe({ error: () => undefined });
  }

  protected fieldAnomaly(field: VitalType) {
    return this.liveAnomalies()[field];
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const hasAnyReading = NUMERIC_FIELDS.some((f) => v[f] !== null);
    if (!hasAnyReading) {
      this.form.markAllAsTouched();
      this.messageService.add({
        severity: 'warn',
        summary: 'Wprowadź co najmniej jeden pomiar.',
      });
      return;
    }

    this.saving.set(true);
    this.vitalsService
      .addVitals({
        patientId: this.patientId(),
        context: v.context,
        systolic: v.systolic ?? undefined,
        diastolic: v.diastolic ?? undefined,
        heartRate: v.heartRate ?? undefined,
        temperature: v.temperature ?? undefined,
        spo2: v.spo2 ?? undefined,
        respiratoryRate: v.respiratoryRate ?? undefined,
        painScore: v.painScore ?? undefined,
        notes: v.notes || undefined,
      })
      .subscribe({
        next: ({ saved, anomalies }) => {
          this.saving.set(false);
          const hasCritical = anomalies.some((a) => a.severity === 'critical');
          if (anomalies.length === 0) {
            this.messageService.add({ severity: 'success', summary: 'Pomiar zapisany.' });
          } else {
            this.messageService.add({
              severity: hasCritical ? 'error' : 'warn',
              summary: hasCritical
                ? 'Pomiar zapisany. Wykryto odchylenia krytyczne.'
                : 'Pomiar zapisany. Wykryto odchylenia.',
            });
          }
          this.resetForm();
          this.saved.emit({ saved, anomalies });
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.showSaveError(toApiError(error).fieldErrors);
        },
      });
  }

  /** Puts 422 `errors[]` (field = measurement name) on the matching controls; the rest goes to a toast. */
  private showSaveError(errors: FieldError[]): void {
    const rest: string[] = [];
    for (const e of errors) {
      const control = (this.form.controls as Record<string, AbstractControl | undefined>)[e.field];
      if (!control) {
        rest.push(e.message);
        continue;
      }
      control.setErrors({ server: e.message });
      control.markAsTouched();
    }
    this.messageService.add({
      severity: 'error',
      summary: 'Nie udało się zapisać pomiaru.',
      detail: rest.join(' ') || undefined,
    });
  }

  private resetForm(): void {
    this.form.reset({
      systolic: null,
      diastolic: null,
      heartRate: null,
      temperature: null,
      spo2: null,
      respiratoryRate: null,
      painScore: null,
      context: 'ward_round',
      notes: '',
    });
  }
}
