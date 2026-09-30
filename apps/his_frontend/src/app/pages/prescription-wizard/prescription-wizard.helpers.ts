import { Validators, type NonNullableFormBuilder } from '@angular/forms';
import type { SummaryItem } from '../../components/summary-list/summary-list';
import {
  computeDailyDose,
  defaultRoute,
  suggestPackageQuantity,
} from '../../components/dosage-editor/dosage-math';
import type { Drug, Patient, PrescriptionDraft, PrescriptionItem, StaffMember } from '../../models';
import { toLocalIsoDate } from '../../utils/date-utils';

export const MAX_ITEMS = 5;
/**
 * "Long-term therapy" heuristic (not specified by the plan; documented per the agent brief):
 * a course is treated as long-term when its duration exceeds 90 days OR the clinician
 * explicitly marks it long-term via the "Leczenie przewlekłe" toggle in step 3. Long-term
 * prescriptions get a 365-day validity window instead of the standard 30 days, matching
 * Polish e-prescription practice for chronic medication.
 * Assessed as reasonable for a demo (real rules also cap by 120 days of therapy per Rx); left as-is.
 */
export const LONG_TERM_DURATION_THRESHOLD_DAYS = 90;
const STANDARD_VALIDITY_DAYS = 30;
const LONG_TERM_VALIDITY_DAYS = 365;

function addDaysLocal(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

export function computeValidUntil(validFrom: Date, longTerm: boolean): string {
  const days = longTerm ? LONG_TERM_VALIDITY_DAYS : STANDARD_VALIDITY_DAYS;
  return toLocalIsoDate(addDaysLocal(validFrom, days));
}

export function createDosageForm(fb: NonNullableFormBuilder) {
  return fb.group({
    dose: fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    doseUnit: fb.control(''),
    route: fb.control<string | null>(null, Validators.required),
    frequency: fb.control<string | null>(null, Validators.required),
    timesOfDay: fb.control<string[]>([]),
    asNeeded: fb.control(false),
    maxPerDay: fb.control<number | null>(null),
    instructions: fb.control(''),
  });
}

export function createQuantityForm(fb: NonNullableFormBuilder) {
  return fb.group({
    durationDays: fb.control<number | null>(30, [
      Validators.required,
      Validators.min(1),
      Validators.max(365),
    ]),
    quantityPackages: fb.control<number | null>(1, [Validators.required, Validators.min(1)]),
    reimbursement: fb.control<string | null>(null, Validators.required),
    substitutionAllowed: fb.control(true),
    longTermOverride: fb.control(false),
  });
}

export type DosageFormValue = ReturnType<ReturnType<typeof createDosageForm>['getRawValue']>;
export type QuantityFormValue = ReturnType<ReturnType<typeof createQuantityForm>['getRawValue']>;

export function buildPrescriptionItem(
  drug: Drug,
  dv: DosageFormValue,
  qv: QuantityFormValue,
): PrescriptionItem {
  return {
    drugId: drug.id,
    drugName: drug.name,
    activeSubstance: drug.activeSubstance,
    strength: drug.strength,
    form: drug.form,
    dosage: {
      dose: dv.dose ?? 0,
      doseUnit: drug.defaultDoseUnit,
      route: dv.route as never,
      frequency: dv.frequency as never,
      timesOfDay: dv.timesOfDay.length ? (dv.timesOfDay as never) : undefined,
      durationDays: qv.durationDays ?? 30,
      asNeeded: dv.asNeeded,
      maxPerDay: dv.asNeeded ? (dv.maxPerDay ?? undefined) : undefined,
      instructions: dv.instructions || undefined,
    },
    quantityPackages: qv.quantityPackages ?? 1,
    reimbursement: (qv.reimbursement ?? 'none') as never,
    substitutionAllowed: qv.substitutionAllowed,
  };
}

export function buildPrescriptionSummary(i: {
  patient: Patient | null;
  patientId: string;
  prescriber: StaffMember;
  itemCount: number;
  validFrom: Date;
  validUntil: string;
  longTerm: boolean;
}): SummaryItem[] {
  return [
    {
      label: 'Pacjent',
      value: i.patient ? `${i.patient.lastName} ${i.patient.firstName}` : i.patientId,
    },
    {
      label: 'Wystawiający',
      value: `${i.prescriber.title} ${i.prescriber.firstName} ${i.prescriber.lastName}`,
    },
    { label: 'Liczba pozycji', value: String(i.itemCount) },
    { label: 'Ważna od', value: toLocalIsoDate(i.validFrom) },
    { label: 'Ważna do', value: i.validUntil },
    { label: 'Leczenie przewlekłe', value: i.longTerm ? 'Tak' : 'Nie' },
  ];
}

/** Step 2 defaults applied when a (new) drug is selected. */
export function dosageDefaultsFor(drug: Drug): DosageFormValue {
  return {
    dose: null,
    doseUnit: drug.defaultDoseUnit,
    route: defaultRoute(drug),
    frequency: null,
    timesOfDay: [],
    asNeeded: false,
    maxPerDay: null,
    instructions: '',
  };
}

/** Step 3 defaults applied when a (new) drug is selected. */
export function quantityDefaultsFor(drug: Drug): QuantityFormValue {
  return {
    durationDays: 30,
    quantityPackages: 1,
    reimbursement: drug.reimbursementOptions[0] ?? null,
    substitutionAllowed: true,
    longTermOverride: false,
  };
}

export function suggestQuantity(drug: Drug, dv: DosageFormValue, days: number | null): number {
  const daily = computeDailyDose({
    dose: dv.dose,
    doseUnit: drug.defaultDoseUnit,
    frequency: dv.frequency as never,
    asNeeded: dv.asNeeded,
    maxPerDay: dv.maxPerDay,
  });
  return suggestPackageQuantity(daily, dv.dose, drug, days);
}

export function buildPrescriptionDraft(i: {
  patientId: string;
  prescriberId: string;
  validFrom: Date;
  validUntil: string;
  items: PrescriptionItem[];
  notes: string[];
}): PrescriptionDraft {
  return {
    patientId: i.patientId,
    prescriberId: i.prescriberId,
    validFrom: toLocalIsoDate(i.validFrom),
    validUntil: i.validUntil,
    kind: 'e_prescription',
    items: i.items,
    notes: i.notes.length ? i.notes.join(' | ') : undefined,
  };
}
