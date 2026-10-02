import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { DatePicker } from 'primeng/datepicker';
import { Select } from 'primeng/select';
import { InputText } from 'primeng/inputtext';
import { Button } from 'primeng/button';
import {
  ALLERGY_CATEGORY_OPTIONS,
  ALLERGY_SEVERITY_OPTIONS,
  ALLERGY_STATUS_WRITE_OPTIONS,
} from '../../constants/labels';
import type { AllergyCategory, AllergySeverity, AllergyStatus, ID } from '../../models';
import type { AllergyCreateRequest } from '../../models/api';
import { FormField } from '../form-field/form-field';

export interface AllergyDialogSave {
  draft: AllergyCreateRequest;
}

/**
 * `p-dialog` form to record a new `Allergy`. Not SNOMED-restricted (free-text substance/reaction);
 * optional ATC codes drive the prescription allergy check, so they're kept even though they're free-form.
 */
@Component({
  selector: 'app-allergy-dialog',
  imports: [Dialog, Select, DatePicker, InputText, Button, ReactiveFormsModule, FormField],
  templateUrl: './allergy-dialog.html',
  styleUrl: './allergy-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'allergy-dialog' },
})
export class AllergyDialog {
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly visible = input.required<boolean>();
  readonly patientId = input.required<ID>();

  readonly visibleChange = output<boolean>();
  readonly save = output<AllergyDialogSave>();

  protected readonly categoryOptions = ALLERGY_CATEGORY_OPTIONS;
  protected readonly severityOptions = ALLERGY_SEVERITY_OPTIONS;
  protected readonly statusOptions = ALLERGY_STATUS_WRITE_OPTIONS;

  protected readonly form = this.fb.group({
    substance: this.fb.control('', [Validators.required, Validators.maxLength(200)]),
    category: this.fb.control<AllergyCategory>('drug', Validators.required),
    reaction: this.fb.control('', [Validators.required, Validators.maxLength(500)]),
    severity: this.fb.control<AllergySeverity>('mild', Validators.required),
    status: this.fb.control<AllergyStatus>('active', Validators.required),
    recordedAt: this.fb.control<Date | null>(null),
    atcCodes: this.fb.control(''),
  });

  private resetForm(): void {
    this.form.reset({
      substance: '',
      category: 'drug',
      reaction: '',
      severity: 'mild',
      status: 'active',
      recordedAt: null,
      atcCodes: '',
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
    const atcCodes = value.atcCodes
      .split(',')
      .map((c) => c.trim().toUpperCase())
      .filter((c) => c.length > 0);

    this.save.emit({
      draft: {
        patientId: this.patientId(),
        substance: value.substance,
        category: value.category,
        reaction: value.reaction,
        severity: value.severity,
        status: value.status,
        recordedAt: value.recordedAt ? value.recordedAt.toISOString() : undefined,
        atcCodes: atcCodes.length ? atcCodes : undefined,
      },
    });
    this.resetForm();
    this.visibleChange.emit(false);
  }
}
