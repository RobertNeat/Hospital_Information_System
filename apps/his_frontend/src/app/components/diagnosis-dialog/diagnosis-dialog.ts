import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { DatePicker } from 'primeng/datepicker';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { Button } from 'primeng/button';
import { catchError, of } from 'rxjs';
import { rxResource } from '@angular/core/rxjs-interop';
import { DIAGNOSIS_STATUS_OPTIONS, DIAGNOSIS_TYPE_OPTIONS } from '../../constants/labels';
import type { Diagnosis, DiagnosisStatus, DiagnosisType, ID } from '../../models';
import type { DiagnosisCreateRequest } from '../../models/api';
import { EhrService } from '../../services/ehr.service';
import { buildDiagnosisOptions, type DiagnosisOption } from '../../utils/diagnosis-options';
import { FormField } from '../form-field/form-field';

export interface DiagnosisDialogSave {
  draft: DiagnosisCreateRequest;
}

/** `p-dialog` form to record a new `Diagnosis` (SNOMED CT picker) for the patient's history. */
@Component({
  selector: 'app-diagnosis-dialog',
  imports: [Dialog, Select, DatePicker, Textarea, Button, ReactiveFormsModule, FormField],
  templateUrl: './diagnosis-dialog.html',
  styleUrl: './diagnosis-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'diagnosis-dialog' },
})
export class DiagnosisDialog {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly ehrService = inject(EhrService);

  readonly visible = input.required<boolean>();
  readonly patientId = input.required<ID>();
  readonly diagnoses = input<Diagnosis[]>([]);

  readonly visibleChange = output<boolean>();
  readonly save = output<DiagnosisDialogSave>();

  protected readonly typeOptions = DIAGNOSIS_TYPE_OPTIONS;
  protected readonly statusOptions = DIAGNOSIS_STATUS_OPTIONS;

  // Snowstorm może być wyłączony/niedostępny - picker ma wtedy po prostu puste podpowiedzi, bez blokowania dialogu.
  private readonly suggestionsResource = rxResource({
    params: () => (this.visible() ? this.patientId() : null),
    stream: () =>
      this.ehrService
        .getSnomedSuggestions('diagnosis')
        .pipe(catchError(() => of({ total: 0, offset: 0, concepts: [] }))),
  });

  protected readonly diagnosisOptions = computed<DiagnosisOption[]>(() =>
    buildDiagnosisOptions(this.diagnoses(), this.suggestionsResource.value()?.concepts ?? []),
  );

  protected readonly noSuggestions = computed(
    () => !this.suggestionsResource.isLoading() && this.diagnosisOptions().length === 0,
  );

  protected readonly form = this.fb.group({
    code: this.fb.control<string | null>(null, Validators.required),
    type: this.fb.control<DiagnosisType>('primary', Validators.required),
    status: this.fb.control<DiagnosisStatus>('active', Validators.required),
    diagnosedAt: this.fb.control<Date | null>(null),
    notes: this.fb.control('', Validators.maxLength(1000)),
  });

  private resetForm(): void {
    this.form.reset({
      code: null,
      type: 'primary',
      status: 'active',
      diagnosedAt: null,
      notes: '',
    });
  }

  protected onHide(): void {
    this.resetForm();
    this.visibleChange.emit(false);
  }

  protected onCancel(): void {
    this.resetForm();
    this.visibleChange.emit(false);
  }

  protected onSave(): void {
    this.form.markAllAsTouched();
    this.form.updateValueAndValidity();
    if (this.form.invalid) return;

    const value = this.form.getRawValue();
    const option = this.diagnosisOptions().find((o) => o.value === value.code);
    if (!option) return;

    this.save.emit({
      draft: {
        patientId: this.patientId(),
        code: option.coding,
        type: value.type,
        status: value.status,
        diagnosedAt: value.diagnosedAt ? value.diagnosedAt.toISOString() : undefined,
        notes: value.notes || undefined,
      },
    });
    this.resetForm();
    this.visibleChange.emit(false);
  }
}
