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
import { MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { Select } from 'primeng/select';
import { Tag } from 'primeng/tag';
import { Textarea } from 'primeng/textarea';
import { VITAL_CONTEXT_OPTIONS } from '../../constants/labels';
import { VITAL_THRESHOLDS } from '../../constants/vitals-thresholds';
import type { VitalAnomaly, VitalSigns, VitalsRecordResponse, VitalType } from '../../models';
import { StaffService } from '../../services/staff.service';
import { VitalsService } from '../../services/vitals.service';
import { evaluateVitals } from '../../utils/vitals-anomaly';
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
  private readonly staffService = inject(StaffService);
  private readonly messageService = inject(MessageService);

  readonly patientId = input.required<string>();
  readonly saved = output<VitalsSaveResult>();

  protected readonly contextOptions = VITAL_CONTEXT_OPTIONS;
  protected readonly thresholds = VITAL_THRESHOLDS;
  protected readonly saving = signal(false);

  protected readonly form = this.fb.group({
    systolic: this.fb.control<number | null>(null, [
      Validators.min(VITAL_THRESHOLDS.systolic.min),
      Validators.max(VITAL_THRESHOLDS.systolic.max),
    ]),
    diastolic: this.fb.control<number | null>(null, [
      Validators.min(VITAL_THRESHOLDS.diastolic.min),
      Validators.max(VITAL_THRESHOLDS.diastolic.max),
    ]),
    heartRate: this.fb.control<number | null>(null, [
      Validators.min(VITAL_THRESHOLDS.heartRate.min),
      Validators.max(VITAL_THRESHOLDS.heartRate.max),
    ]),
    temperature: this.fb.control<number | null>(null, [
      Validators.min(VITAL_THRESHOLDS.temperature.min),
      Validators.max(VITAL_THRESHOLDS.temperature.max),
    ]),
    spo2: this.fb.control<number | null>(null, [
      Validators.min(VITAL_THRESHOLDS.spo2.min),
      Validators.max(VITAL_THRESHOLDS.spo2.max),
    ]),
    respiratoryRate: this.fb.control<number | null>(null, [
      Validators.min(VITAL_THRESHOLDS.respiratoryRate.min),
      Validators.max(VITAL_THRESHOLDS.respiratoryRate.max),
    ]),
    painScore: this.fb.control<number | null>(null, [Validators.min(0), Validators.max(10)]),
    context: this.fb.control<'office_exam' | 'ward_round' | 'triage' | 'observation'>(
      'ward_round',
      [Validators.required],
    ),
    notes: this.fb.control(''),
  });

  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.value });

  /** Live per-field anomaly, computed with `evaluateVitals` from whatever has been typed so far. */
  protected readonly liveAnomalies = computed<Partial<Record<VitalType, VitalAnomaly>>>(() => {
    const v = this.formValue();
    const draft: VitalSigns = {
      id: '',
      patientId: '',
      recordedAt: new Date().toISOString(),
      recordedById: '',
      context: v.context ?? 'ward_round',
      systolic: v.systolic ?? undefined,
      diastolic: v.diastolic ?? undefined,
      heartRate: v.heartRate ?? undefined,
      temperature: v.temperature ?? undefined,
      spo2: v.spo2 ?? undefined,
      respiratoryRate: v.respiratoryRate ?? undefined,
    };
    const anomalies = evaluateVitals(draft);
    const map: Partial<Record<VitalType, VitalAnomaly>> = {};
    for (const a of anomalies) map[a.type] = a;
    return map;
  });

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
        recordedAt: new Date().toISOString(),
        recordedById: this.staffService.currentUser().id,
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
      .subscribe(({ saved, anomalies }) => {
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
