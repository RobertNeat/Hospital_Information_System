import { computed, inject, type Signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import type { Observable } from 'rxjs';
import { MessageService } from 'primeng/api';
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
import {
  buildDiagnosisOptions,
  injectDiagnosisSearch,
  type DiagnosisOption,
} from '../../utils/diagnosis-options';

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
  contrastSafetyUnverified: boolean;
}): boolean {
  if (i.isMri && i.implantOrMetal) return true;
  if (i.contrastSafetyUnverified) return true;
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
  'patientId' | 'laterality' | 'contrast' | 'clinicalIndication' | 'urgency'
> & {
  exam: ImagingExam;
  clinicalQuestion: string;
  diagnosis: DiagnosisOption | undefined;
  slot: ScheduleSlot | null;
  /** Raw step 3 form value; only the fields belonging to the order's safety block are used. */
  safety: ImagingOrderCreateRequest['safety'] & Record<string, unknown>;
  creatinineEgfr: CreatinineEgfr | null;
};

export function buildImagingOrderCreateRequest(
  i: ImagingOrderPayloadInput,
): ImagingOrderCreateRequest {
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
  };
}

/** Loads diagnoses/allergies/labs for the patient and derives the wizard's reference data. Injection context only. */
export function injectImagingPatientData(patientId: Signal<string>) {
  const ehrService = inject(EhrService);
  const labResultService = inject(LabResultService);
  const toast = inject(MessageService);
  // Rozpoznania/Snowstorm nie zasilaja bezpieczenstwa kontrastu - ich brak nie blokuje kroku 3,
  // tylko toastuje i oddaje puste dane.
  function unverified<T>(source: Observable<T[]>, label: string): Observable<T[]> {
    return source.pipe(
      catchError(() => {
        toast.add({
          severity: 'warn',
          summary: 'Bezpieczeństwo badania',
          detail: `Nie udało się zweryfikować: ${label}.`,
        });
        return of<T[]>([]);
      }),
    );
  }
  // Alergie i wyniki lab. zasilaja hasContrastAllergy/latestCreatinineEgfr (bezpieczenstwo kontrastu).
  // Fail-closed: blad zapytania oddaje `null` (nie `[]`), zeby nie udawac "zweryfikowano, brak alergii/eGFR
  // w normie" - wolacy rozroznia ten stan przez `contrastSafetyDataUnavailable` i blokuje zlecenie z kontrastem.
  function unverifiedSafetyData<T>(source: Observable<T[]>, label: string): Observable<T[] | null> {
    return source.pipe(
      catchError(() => {
        toast.add({
          severity: 'warn',
          summary: 'Bezpieczeństwo badania',
          detail: `Nie udało się zweryfikować: ${label}.`,
        });
        return of<T[] | null>(null);
      }),
    );
  }
  const resource = rxResource({
    params: () => patientId(),
    stream: ({ params: pid }) =>
      forkJoin({
        diagnoses: unverified(ehrService.getDiagnoses(pid), 'rozpoznania pacjenta'),
        // Snowstorm może być wyłączony/niedostępny - nie może blokować allergies/labResults (bezpieczeństwo badania).
        suggestions: ehrService
          .getSnomedSuggestions('diagnosis')
          .pipe(catchError(() => of({ total: 0, offset: 0, concepts: [] }))),
        allergies: unverifiedSafetyData(ehrService.getAllergies(pid), 'alergie pacjenta'),
        labResults: unverifiedSafetyData(
          labResultService.getResults(pid),
          'wyniki badań laboratoryjnych',
        ),
      }),
  });
  const baseDiagnosisOptions = computed<DiagnosisOption[]>(() => {
    const data = resource.value();
    return data ? buildDiagnosisOptions(data.diagnoses, data.suggestions.concepts) : [];
  });
  const patientDiagnoses = computed(() => resource.value()?.diagnoses ?? []);
  const diagnosisSearch = injectDiagnosisSearch(baseDiagnosisOptions, patientDiagnoses);

  const allergiesUnavailable = computed(() => resource.value()?.allergies === null);
  const labResultsUnavailable = computed(() => resource.value()?.labResults === null);

  return {
    diagnosisOptions: diagnosisSearch.options,
    searchDiagnosis: diagnosisSearch.search,
    hasContrastAllergy: computed(() => {
      const allergies = resource.value()?.allergies;
      return allergies ? hasActiveContrastAllergy(allergies) : false;
    }),
    latestCreatinineEgfr: computed<CreatinineEgfr | null>(() => {
      const labResults = resource.value()?.labResults;
      return labResults ? latestCreatinineEgfr(labResults) : null;
    }),
    allergiesUnavailable,
    labResultsUnavailable,
    // Dopoki trwa pierwsze wczytanie (resource.value() === undefined) dane rowniez nie sa zweryfikowane -
    // fail-closed obejmuje loading, nie tylko blad zapytania.
    contrastSafetyDataUnavailable: computed(
      () => resource.value() === undefined || allergiesUnavailable() || labResultsUnavailable(),
    ),
  };
}

/** Derived step 3 (patient safety) state. Reads form values lazily, like the original computeds. */
export function createSafetyState(deps: {
  step1Form: Step1Form;
  step3Form: Step3Form;
  patient: Signal<Patient | null>;
  egfr: Signal<CreatinineEgfr | null>;
  contrastSafetyDataUnavailable: Signal<boolean>;
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
  // Fail-closed: zlecenie z kontrastem wymaga albo poprawnie wczytanych danych alergii/eGFR, albo
  // rezygnacji z kontrastu, albo odnotowania przez lekarza rzeczywistej alergii (contrastAllergy=true,
  // np. do premedykacji) - to pole oznacza "pacjent JEST uczulony", nie "zweryfikowano brak alergii",
  // więc nie jest furtką do potwierdzania bezpieczeństwa innym kanałem. Zlecenia bez kontrastu nie są
  // objęte tą blokadą.
  const contrastSafetyUnverified = computed(
    () => step1().contrast && deps.contrastSafetyDataUnavailable() && !step3().contrastAllergy,
  );
  const step3Blocked = computed(() => {
    const s3 = step3();
    return isSafetyBlocked({
      isMri: isMri(),
      implantOrMetal: s3.pacemakerOrImplant || s3.metalFragments,
      egfrBlocksContrast: egfrBlocksContrast(),
      egfrConfirmed: s3.egfrConfirmed,
      contrastSafetyUnverified: contrastSafetyUnverified(),
    });
  });
  return {
    needsPregnancyCheck: needs,
    isMri,
    egfrBlocksContrast,
    contrastSafetyUnverified,
    step3Blocked,
  };
}
