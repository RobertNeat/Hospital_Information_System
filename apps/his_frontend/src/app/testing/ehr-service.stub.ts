import { of } from 'rxjs';
import { ALLERGIES } from '../mock-data/allergies.mock';
import { CLINICAL_NOTES } from '../mock-data/clinical-notes.mock';
import { CONTRAINDICATIONS } from '../mock-data/contraindications.mock';
import { DIAGNOSES } from '../mock-data/diagnoses.mock';
import { ENCOUNTERS } from '../mock-data/encounters.mock';
import { EPISODES } from '../mock-data/episodes.mock';
import { TREATMENTS } from '../mock-data/treatments.mock';
import type { Allergy, ClinicalNote, Diagnosis, EhrSummary } from '../models';
import type {
  AllergyCreateRequest,
  ClinicalNoteCreateRequest,
  DiagnosisCreateRequest,
  SnomedConceptPage,
} from '../models/api';
import { EhrService } from '../services/ehr.service';

const forPatient = <T extends { patientId: string }>(items: readonly T[], pid: string): T[] =>
  items.filter((i) => i.patientId === pid);

/** In-memory `EhrService` double over the mock EHR data (fresh state per TestBed). */
export function createEhrServiceStub(): Partial<Record<keyof EhrService, unknown>> {
  const notes: ClinicalNote[] = structuredClone(CLINICAL_NOTES);
  const diagnoses: Diagnosis[] = structuredClone(DIAGNOSES);
  const allergies: Allergy[] = structuredClone(ALLERGIES);
  return {
    getSummary: (pid: string) =>
      of<EhrSummary>({
        recentDiagnoses: forPatient(diagnoses, pid).slice(0, 5),
        chronicConditions: forPatient(diagnoses, pid).filter((d) => d.type === 'chronic'),
        activeMedications: [],
        recentEncounters: forPatient(ENCOUNTERS, pid).slice(0, 5),
        allergies: forPatient(allergies, pid),
      }),
    getEncounters: (pid: string) => of(forPatient(ENCOUNTERS, pid)),
    getEpisodes: (pid: string) => of(forPatient(EPISODES, pid)),
    getNotes: (pid: string) => of(forPatient(notes, pid)),
    addNote: (draft: ClinicalNoteCreateRequest) => {
      const note: ClinicalNote = {
        ...draft,
        id: `test-note-${notes.length + 1}`,
        authorId: 'test-staff',
        createdAt: new Date().toISOString(),
      };
      notes.push(note);
      return of(note);
    },
    getDiagnoses: (pid: string) => of(forPatient(diagnoses, pid)),
    addDiagnosis: (pid: string, draft: DiagnosisCreateRequest) => {
      const diagnosis: Diagnosis = {
        ...draft,
        id: `test-diagnosis-${diagnoses.length + 1}`,
        patientId: pid,
        status: draft.status ?? 'active',
        diagnosedAt: draft.diagnosedAt ?? new Date().toISOString(),
        diagnosedById: 'test-staff',
      };
      diagnoses.push(diagnosis);
      return of(diagnosis);
    },
    getAllergies: (pid: string) => of(forPatient(allergies, pid)),
    addAllergy: (pid: string, draft: AllergyCreateRequest) => {
      const allergy: Allergy = {
        ...draft,
        id: `test-allergy-${allergies.length + 1}`,
        patientId: pid,
        status: draft.status ?? 'active',
        recordedAt: draft.recordedAt ?? new Date().toISOString(),
      };
      allergies.push(allergy);
      return of(allergy);
    },
    getContraindications: (pid: string) => of(forPatient(CONTRAINDICATIONS, pid)),
    getTreatments: (pid: string) => of(forPatient(TREATMENTS, pid)),
    getSnomedSuggestions: () =>
      of<SnomedConceptPage>({
        total: DIAGNOSES.length,
        offset: 0,
        concepts: DIAGNOSES.filter((d) => d.code.system === 'SNOMED').map((d) => ({
          code: d.code.code,
          display: d.code.display,
        })),
      }),
  };
}

/** Test provider replacing the HTTP-backed `EhrService` with the mock EHR data. */
export const ehrServiceStub = {
  provide: EhrService,
  useFactory: createEhrServiceStub,
};
