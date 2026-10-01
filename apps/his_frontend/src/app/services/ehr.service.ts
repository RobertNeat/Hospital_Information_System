import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import {
  ICD10_URL,
  patientAllergiesUrl,
  patientClinicalNotesUrl,
  patientContraindicationsUrl,
  patientDiagnosesUrl,
  patientEhrSummaryUrl,
  patientEncountersUrl,
  patientEpisodesUrl,
  patientTreatmentsUrl,
} from '../config/api.config';
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
import { toHttpParams } from '../utils/http-params';

/** Backend maximum for the ICD-10 dictionary page (`size` above it is truncated). */
const ICD10_SIZE = 100;

/**
 * Patient EHR backed by `/patients/{id}/...`. The summary is a backend projection. Roles with
 * only `ehr:read-limited` may read diagnoses, allergies, contraindications and treatments.
 */
@Injectable({ providedIn: 'root' })
export class EhrService {
  private readonly http = inject(HttpClient);

  getSummary(pid: ID): Observable<EhrSummary> {
    return this.http.get<EhrSummary>(patientEhrSummaryUrl(pid));
  }

  getEncounters(pid: ID): Observable<Encounter[]> {
    return this.http.get<Encounter[]>(patientEncountersUrl(pid));
  }

  getEpisodes(pid: ID): Observable<TreatmentEpisode[]> {
    return this.http.get<TreatmentEpisode[]>(patientEpisodesUrl(pid));
  }

  getNotes(pid: ID): Observable<ClinicalNote[]> {
    return this.http.get<ClinicalNote[]>(patientClinicalNotesUrl(pid));
  }

  /** The author comes from the token; the allowed categories depend on the role (403 otherwise). */
  addNote(draft: ClinicalNoteCreateRequest): Observable<ClinicalNote> {
    return this.http.post<ClinicalNote>(patientClinicalNotesUrl(draft.patientId), draft);
  }

  getDiagnoses(pid: ID): Observable<Diagnosis[]> {
    return this.http.get<Diagnosis[]>(patientDiagnosesUrl(pid));
  }

  getAllergies(pid: ID): Observable<Allergy[]> {
    return this.http.get<Allergy[]>(patientAllergiesUrl(pid));
  }

  getContraindications(pid: ID): Observable<Contraindication[]> {
    return this.http.get<Contraindication[]>(patientContraindicationsUrl(pid));
  }

  getTreatments(pid: ID): Observable<Treatment[]> {
    return this.http.get<Treatment[]>(patientTreatmentsUrl(pid));
  }

  /** ICD-10 codes for the diagnosis picker (optional `term` filters by code or name). */
  getIcd10Dictionary(term?: string): Observable<Coding[]> {
    return this.http.get<Coding[]>(ICD10_URL, { params: toHttpParams({ term, size: ICD10_SIZE }) });
  }
}
