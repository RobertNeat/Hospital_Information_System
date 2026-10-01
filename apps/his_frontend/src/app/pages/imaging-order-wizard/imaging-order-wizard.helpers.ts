import { computed, inject, type Signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import type { SummaryItem } from '../../components/summary-list/summary-list';
import {
  IMAGING_MODALITY_OPTIONS,
  LATERALITY_OPTIONS,
  URGENCY_OPTIONS,
} from '../../constants/labels';
import type { Step1Form, Step3Form } from './imaging-order-wizard.forms';
import type {
  Allergy,
  ImagingExam,
  ImagingModality,
  ImagingOrderCreateRequest,
  LabResult,
  Patient,
  Laterality,
  OrderUrgency,
  ScheduleSlot,
} from '../../models';
import { ageFromBirthDate } from '../../utils/date-utils';
import { rawValueSignal } from '../../utils/form-signals';
import { EhrService } from '../../services/ehr.service';
import { LabResultService } from '../../services/lab-result.service';
import { buildDiagnosisOptions, type DiagnosisOption } from '../../utils/diagnosis-options';

const PREGNANCY_MODALITIES: ImagingModality[] = ['RTG', 'CT', 'MMG', 'ANGIOGRAPHY'];

/** ATC group for iodine/gadolinium contrast media allergies (mock-data convention). */
const CONTRAST_ATC_PREFIX = 'V08';

export interface CreatinineEgfr {
  creatinine?: number;
  egfr?: number;
  collectedAt?: string;
}

function hasActiveContrastAllergy(allergies: Allergy[]): boolean {
  return allergies.some(
    (a) => a.status === 'active' && a.atcCodes?.some((c) => c.startsWith(CONTRAST_ATC_PREFIX)),
  );
}

function latestCreatinineEgfr(labResults: LabResult[]): CreatinineEgfr | null {
  const withCrea = labResults
    .filter((r) => r.observations.some((o) => o.analyteCode === 'EGFR' || o.analyteCode === 'KREA'))
    .sort((a, b) => b.collectedAt.localeCompare(a.collectedAt));
  const latest = withCrea[0];
  if (!latest) return null;
  const crea = latest.observations.find((o) => o.analyteCode === 'KREA');
  const egfr = latest.observations.find((o) => o.analyteCode === 'EGFR');
  return {
    creatinine: typeof crea?.value === 'number' ? crea.value : undefined,
    egfr: typeof egfr?.value === 'number' ? egfr.value : undefined,
    collectedAt: latest.collectedAt,
  };
}

export function needsPregnancyCheck(
  modality: ImagingModality | null,
  age: number | null,
  gender: string | undefined,
): boolean {
  if (!modality || !PREGNANCY_MODALITIES.includes(modality)) return false;
  if (gender !== 'female') return false;
  if (age === null) return false;
  return age >= 12 && age <= 55;
}

function isSafetyBlocked(i: {
  isMri: boolean;
  implantOrMetal: boolean;
  egfrBlocksContrast: boolean;
  egfrConfirmed: boolean;
}): boolean {
  if (i.isMri && i.implantOrMetal) return true;
  return i.egfrBlocksContrast && !i.egfrConfirmed;
}

export interface ImagingSummaryInput {
  exam: ImagingExam | null;
  modality: ImagingModality | null;
  laterality: Laterality;
  contrast: boolean;
  urgency: OrderUrgency;
  diagnosis: DiagnosisOption | undefined;
  clinicalIndication: string;
  immediate: boolean;
  slot: ScheduleSlot | null;
}

export function buildImagingSummary(i: ImagingSummaryInput): SummaryItem[] {
  const modalityLabel = IMAGING_MODALITY_OPTIONS.find((o) => o.value === i.modality)?.label ?? '';
  const lateralityLabel = LATERALITY_OPTIONS.find((o) => o.value === i.laterality)?.label ?? '';
  const urgencyLabel = URGENCY_OPTIONS.find((o) => o.value === i.urgency)?.label ?? '';
  return [
    { label: 'Badanie', value: i.exam ? `${i.exam.name} (${modalityLabel})` : '—' },
    { label: 'Strona', value: i.exam?.requiresLaterality ? lateralityLabel : '—' },
    { label: 'Kontrast', value: i.contrast ? 'Tak' : 'Nie' },
    { label: 'Pilność', value: urgencyLabel },
    { label: 'Rozpoznanie', value: i.diagnosis?.label ?? '—' },
    { label: 'Wskazania kliniczne', value: i.clinicalIndication },
    {
      label: 'Termin',
      value: i.immediate
        ? 'Wykonanie natychmiastowe (bez terminu)'
        : i.slot
          ? `${new Date(i.slot.start).toLocaleString('pl-PL')} (${i.slot.room})`
          : '—',
    },
  ];
}

export type ImagingOrderPayloadInput = Pick<
  ImagingOrderCreateRequest,
  'patientId' | 'orderedById' | 'laterality' | 'contrast' | 'clinicalIndication' | 'urgency'
> & {
  exam: ImagingExam;
  clinicalQuestion: string;
  diagnosis: DiagnosisOption | undefined;
  slot: ScheduleSlot | null;
  /** Raw step 3 form value; only the fields belonging to the order's safety block are used. */
  safety: ImagingOrderCreateRequest['safety'] & Record<string, unknown>;
  creatinineEgfr: CreatinineEgfr | null;
};

export function buildImagingOrderDraft(i: ImagingOrderPayloadInput): ImagingOrderCreateRequest {
  return {
    patientId: i.patientId,
    examCode: i.exam.code,
    examName: i.exam.name,
    modality: i.exam.modality,
    bodyRegion: i.exam.bodyRegion,
    laterality: i.laterality,
    contrast: i.contrast,
    clinicalIndication: i.clinicalIndication,
    clinicalQuestion: i.clinicalQuestion || undefined,
    diagnosisCode: i.diagnosis?.coding,
    urgency: i.urgency,
    safety: {
      pregnancy: i.safety.pregnancy,
      pacemakerOrImplant: i.safety.pacemakerOrImplant,
      metalFragments: i.safety.metalFragments,
      contrastAllergy: i.safety.contrastAllergy,
      claustrophobia: i.safety.claustrophobia,
      confirmed: i.safety.confirmed,
      creatinine: i.creatinineEgfr?.creatinine,
      egfr: i.creatinineEgfr?.egfr,
    },
    slotId: i.slot?.id,
    orderedById: i.orderedById,
  };
}

/** Loads diagnoses/allergies/labs for the patient and derives the wizard's reference data. Injection context only. */
export function injectImagingPatientData(patientId: Signal<string>) {
  const ehrService = inject(EhrService);
  const labResultService = inject(LabResultService);
  const resource = rxResource({
    params: () => patientId(),
    stream: ({ params: pid }) =>
      forkJoin({
        diagnoses: ehrService.getDiagnoses(pid),
        icd10: ehrService.getIcd10Dictionary(),
        allergies: ehrService.getAllergies(pid),
        labResults: labResultService.getResults(pid),
      }),
  });
  return {
    diagnosisOptions: computed<DiagnosisOption[]>(() => {
      const data = resource.value();
      return data ? buildDiagnosisOptions(data.diagnoses, data.icd10) : [];
    }),
    hasContrastAllergy: computed(() => {
      const data = resource.value();
      return data ? hasActiveContrastAllergy(data.allergies) : false;
    }),
    latestCreatinineEgfr: computed<CreatinineEgfr | null>(() => {
      const data = resource.value();
      return data ? latestCreatinineEgfr(data.labResults) : null;
    }),
  };
}

/** Derived step 3 (patient safety) state. Reads form values lazily, like the original computeds. */
export function createSafetyState(deps: {
  step1Form: Step1Form;
  step3Form: Step3Form;
  patient: Signal<Patient | null>;
  egfr: Signal<CreatinineEgfr | null>;
}) {
  // Reactive forms are not signals: track their values through value-change signals.
  const step1 = rawValueSignal(deps.step1Form);
  const step3 = rawValueSignal(deps.step3Form);
  const patientAge = computed<number | null>(() => {
    const p = deps.patient();
    return p ? ageFromBirthDate(p.birthDate) : null;
  });
  const needs = computed(() =>
    needsPregnancyCheck(step1().modality, patientAge(), deps.patient()?.gender),
  );
  const isMri = computed(() => step1().modality === 'MRI');
  const egfrBlocksContrast = computed(() => {
    const egfr = deps.egfr()?.egfr;
    return step1().contrast && typeof egfr === 'number' && egfr < 30;
  });
  const step3Blocked = computed(() => {
    const s3 = step3();
    return isSafetyBlocked({
      isMri: isMri(),
      implantOrMetal: s3.pacemakerOrImplant || s3.metalFragments,
      egfrBlocksContrast: egfrBlocksContrast(),
      egfrConfirmed: s3.egfrConfirmed,
    });
  });
  return { needsPregnancyCheck: needs, isMri, egfrBlocksContrast, step3Blocked };
}
