import { Injectable, inject } from '@angular/core';
import { forkJoin, map } from 'rxjs';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { ALLERGIES } from '../mock-data/allergies.mock';
import { CLINICAL_NOTES } from '../mock-data/clinical-notes.mock';
import { CONTRAINDICATIONS } from '../mock-data/contraindications.mock';
import { DIAGNOSES, ICD10_DICTIONARY } from '../mock-data/diagnoses.mock';
import { ENCOUNTERS } from '../mock-data/encounters.mock';
import { EPISODES } from '../mock-data/episodes.mock';
import { TREATMENTS } from '../mock-data/treatments.mock';
import type {
  Allergy,
  ClinicalNote,
  Coding,
  Contraindication,
  Diagnosis,
  EhrSummary,
  Encounter,
  ID,
  Treatment,
  TreatmentEpisode,
} from '../models';
import type { ClinicalNoteCreateRequest } from '../models/api';
import { mockResponse, nextId } from '../utils/mock-response';
import { PrescriptionService } from './prescription.service';

@Injectable({ providedIn: 'root' })
export class EhrService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly prescriptionService = inject(PrescriptionService);

  private readonly encounters: Encounter[] = structuredClone(ENCOUNTERS);
  private readonly episodes: TreatmentEpisode[] = structuredClone(EPISODES);
  private readonly notes: ClinicalNote[] = structuredClone(CLINICAL_NOTES);
  private readonly diagnoses: Diagnosis[] = structuredClone(DIAGNOSES);
  private readonly allergies: Allergy[] = structuredClone(ALLERGIES);
  private readonly contraindications: Contraindication[] = structuredClone(CONTRAINDICATIONS);
  private readonly treatments: Treatment[] = structuredClone(TREATMENTS);
  private noteSequence = this.notes.length;

  // mock-only: backend authoritative
  getSummary(pid: ID): Observable<EhrSummary> {
    return forkJoin({
      diagnoses: this.getDiagnoses(pid),
      encounters: this.getEncounters(pid),
      allergies: this.getAllergies(pid),
      activeMedications: this.prescriptionService.getActiveMedications(pid),
    }).pipe(
      map(({ diagnoses, encounters, allergies, activeMedications }) => ({
        recentDiagnoses: [...diagnoses]
          .sort((a, b) => b.diagnosedAt.localeCompare(a.diagnosedAt))
          .slice(0, 5),
        chronicConditions: diagnoses.filter((d) => d.type === 'chronic'),
        activeMedications,
        recentEncounters: [...encounters]
          .sort((a, b) => b.startAt.localeCompare(a.startAt))
          .slice(0, 5),
        allergies,
      })),
    );
  }

  getEncounters(pid: ID): Observable<Encounter[]> {
    return mockResponse(
      this.encounters.filter((e) => e.patientId === pid),
      this.latency,
    );
  }

  getEpisodes(pid: ID): Observable<TreatmentEpisode[]> {
    return mockResponse(
      this.episodes.filter((e) => e.patientId === pid),
      this.latency,
    );
  }

  getNotes(pid: ID): Observable<ClinicalNote[]> {
    return mockResponse(
      this.notes.filter((n) => n.patientId === pid),
      this.latency,
    );
  }

  addNote(draft: ClinicalNoteCreateRequest): Observable<ClinicalNote> {
    this.noteSequence++;
    const note: ClinicalNote = {
      ...draft,
      id: nextId('note', this.noteSequence),
      createdAt: new Date().toISOString(),
    };
    this.notes.push(note);
    return mockResponse(note, this.latency);
  }

  getDiagnoses(pid: ID): Observable<Diagnosis[]> {
    return mockResponse(
      this.diagnoses.filter((d) => d.patientId === pid),
      this.latency,
    );
  }

  getAllergies(pid: ID): Observable<Allergy[]> {
    return mockResponse(
      this.allergies.filter((a) => a.patientId === pid),
      this.latency,
    );
  }

  getContraindications(pid: ID): Observable<Contraindication[]> {
    return mockResponse(
      this.contraindications.filter((c) => c.patientId === pid),
      this.latency,
    );
  }

  getTreatments(pid: ID): Observable<Treatment[]> {
    return mockResponse(
      this.treatments.filter((t) => t.patientId === pid),
      this.latency,
    );
  }

  /** ~40 common ICD-10 codes for the diagnosis picker. */
  getIcd10Dictionary(): Observable<Coding[]> {
    const codings: Coding[] = ICD10_DICTIONARY.map((d) => ({
      system: 'ICD-10',
      code: d.code,
      display: d.display,
    }));
    return mockResponse(codings, this.latency);
  }
}
