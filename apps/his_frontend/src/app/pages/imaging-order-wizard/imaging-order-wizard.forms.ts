import { type AbstractControl, type NonNullableFormBuilder, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { Observable } from 'rxjs';
import type {
  ImagingExam,
  ImagingModality,
  Laterality,
  OrderUrgency,
  PregnancyStatus,
  ScheduleSlot,
} from '../../models';
import type { DiagnosisOption } from '../../utils/diagnosis-options';
import { toLocalIsoDate } from '../../utils/date-utils';

export function createStep1Form(fb: NonNullableFormBuilder) {
  return fb.group({
    modality: fb.control<ImagingModality | null>(null, { validators: [Validators.required] }),
    examCode: fb.control<string | null>(null, { validators: [Validators.required] }),
    laterality: fb.control<Laterality>('na'),
    contrast: fb.control(false),
  });
}

export function createStep2Form(fb: NonNullableFormBuilder) {
  return fb.group({
    clinicalIndication: fb.control('', {
      validators: [Validators.required, Validators.minLength(20)],
    }),
    clinicalQuestion: fb.control(''),
    diagnosisCode: fb.control<DiagnosisOption | null>(null),
    urgency: fb.control<OrderUrgency>('routine', { validators: [Validators.required] }),
  });
}

export function createStep3Form(fb: NonNullableFormBuilder) {
  return fb.group({
    pregnancy: fb.control<PregnancyStatus>('na'),
    pacemakerOrImplant: fb.control(false),
    metalFragments: fb.control(false),
    contrastAllergy: fb.control(false),
    egfrConfirmed: fb.control(false),
    claustrophobia: fb.control(false),
    confirmed: fb.control(false, { validators: [Validators.requiredTrue] }),
  });
}

export function createStep4Form(fb: NonNullableFormBuilder) {
  return fb.group({
    date: fb.control<Date>(new Date()),
    slotId: fb.control<string | null>(null),
    immediate: fb.control(false),
  });
}

export type Step1Form = ReturnType<typeof createStep1Form>;
export type Step3Form = ReturnType<typeof createStep3Form>;

/**
 * Wires the dependent-field resets/validators of step 1 (modality -> exam -> laterality/contrast).
 * Must be called from an injection context (uses `takeUntilDestroyed`).
 */
export function bindStep1Rules(form: Step1Form, selectedExam: () => ImagingExam | null): void {
  const c = form.controls;
  c.modality.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
    c.examCode.setValue(null);
    c.contrast.setValue(false);
    c.laterality.setValue('na');
  });

  c.examCode.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
    const exam = selectedExam();
    if (exam?.requiresLaterality) {
      c.laterality.setValidators([
        Validators.required,
        (ctrl: AbstractControl) => (ctrl.value === 'na' ? { required: true } : null),
      ]);
    } else {
      c.laterality.clearValidators();
      c.laterality.setValue('na');
    }
    c.laterality.updateValueAndValidity();
    if (!exam?.contrastPossible) {
      c.contrast.setValue(false);
    }
  });
}

/** Scans forward up to 14 days for the first available slot of the modality; calls `found` once. */
export function findNearestSlot(
  getSlots: (modality: ImagingModality, iso: string) => Observable<ScheduleSlot[]>,
  modality: ImagingModality,
  found: (date: Date, slot: ScheduleSlot) => void,
): void {
  const startDate = new Date();
  const tryDay = (offset: number): void => {
    if (offset > 14) return;
    const d = new Date(startDate);
    d.setDate(d.getDate() + offset);
    getSlots(modality, toLocalIsoDate(d)).subscribe((slots) => {
      const now = new Date();
      const available = slots
        .filter((s) => s.available && new Date(s.start) > now)
        .sort((a, b) => a.start.localeCompare(b.start));
      if (available.length) {
        found(d, available[0]);
      } else {
        tryDay(offset + 1);
      }
    });
  };
  tryDay(0);
}
