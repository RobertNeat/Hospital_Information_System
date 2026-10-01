import { of } from 'rxjs';
import { ALLERGIES } from '../mock-data/allergies.mock';
import { CLINICAL_NOTES } from '../mock-data/clinical-notes.mock';
import { CONTRAINDICATIONS } from '../mock-data/contraindications.mock';
import { DIAGNOSES, ICD10_DICTIONARY } from '../mock-data/diagnoses.mock';
import { ENCOUNTERS } from '../mock-data/encounters.mock';
import { EPISODES } from '../mock-data/episodes.mock';
import { TREATMENTS } from '../mock-data/treatments.mock';
import type { ClinicalNote, Coding, EhrSummary } from '../models';
import type { ClinicalNoteCreateRequest } from '../models/api';
import { EhrService } from '../services/ehr.service';

const forPatient = <T extends { patientId: string }>(items: readonly T[], pid: string): T[] =>
  items.filter((i) => i.patientId === pid);

/** In-memory `EhrService` double over the mock EHR data (fresh state per TestBed). */
export function createEhrServiceStub(): Partial<Record<keyof EhrService, unknown>> {
  const notes: ClinicalNote[] = structuredClone(CLINICAL_NOTES);
  return {
    getSummary: (pid: string) =>
      of<EhrSummary>({
        recentDiagnoses: forPatient(DIAGNOSES, pid).slice(0, 5),
        chronicConditions: forPatient(DIAGNOSES, pid).filter((d) => d.type === 'chronic'),
        activeMedications: [],
        recentEncounters: forPatient(ENCOUNTERS, pid).slice(0, 5),
        allergies: forPatient(ALLERGIES, pid),
      }),
    getEncounters: (pid: string) => of(forPatient(ENCOUNTERS, pid)),
    getEpisodes: (pid: string) => of(forPatient(EPISODES, pid)),
    getNotes: (pid: string) => of(forPatient(notes, pid)),
    addNote: (draft: ClinicalNoteCreateRequest) => {
      const note: ClinicalNote = {
        ...draft,
        id: `test-note-${notes.length + 1}`,
        createdAt: new Date().toISOString(),
      };
      notes.push(note);
      return of(note);
    },
    getDiagnoses: (pid: string) => of(forPatient(DIAGNOSES, pid)),
    getAllergies: (pid: string) => of(forPatient(ALLERGIES, pid)),
    getContraindications: (pid: string) => of(forPatient(CONTRAINDICATIONS, pid)),
    getTreatments: (pid: string) => of(forPatient(TREATMENTS, pid)),
    getIcd10Dictionary: () =>
      of(
        ICD10_DICTIONARY.map((d): Coding => ({
          system: 'ICD-10',
          code: d.code,
          display: d.display,
        })),
      ),
  };
}

/** Test provider replacing the HTTP-backed `EhrService` with the mock EHR data. */
export const ehrServiceStub = {
  provide: EhrService,
  useFactory: createEhrServiceStub,
};
