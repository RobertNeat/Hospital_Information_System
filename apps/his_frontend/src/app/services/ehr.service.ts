import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import {
  SNOMED_SUGGESTIONS_URL,
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
  Contraindication,
  Diagnosis,
  EhrSummary,
  Encounter,
  ID,
  Treatment,
  TreatmentEpisode,
} from '../models';
import type {
  AllergyCreateRequest,
  ClinicalNoteCreateRequest,
  DiagnosisCreateRequest,
  SnomedConceptPage,
  TerminologyKind,
} from '../models/api';
import { toHttpParams } from '../utils/http-params';

/** Page size for the terminology suggestion picker. */
const SUGGESTIONS_SIZE = 100;

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

  /** `ehr:diagnosis:write` (lekarz). `code` musi byc SNOMED/SCTID (422 w innym przypadku). */
  addDiagnosis(pid: ID, draft: DiagnosisCreateRequest): Observable<Diagnosis> {
    return this.http.post<Diagnosis>(patientDiagnosesUrl(pid), draft);
  }

  getAllergies(pid: ID): Observable<Allergy[]> {
    return this.http.get<Allergy[]>(patientAllergiesUrl(pid));
  }

  /** `ehr:allergy:write` (lekarz, pielegniarka). */
  addAllergy(pid: ID, draft: AllergyCreateRequest): Observable<Allergy> {
    return this.http.post<Allergy>(patientAllergiesUrl(pid), draft);
  }

  getContraindications(pid: ID): Observable<Contraindication[]> {
    return this.http.get<Contraindication[]>(patientContraindicationsUrl(pid));
  }

  getTreatments(pid: ID): Observable<Treatment[]> {
    return this.http.get<Treatment[]>(patientTreatmentsUrl(pid));
  }

  /**
   * SNOMED CT suggestions for the diagnosis/symptom/procedure picker, narrowed by the logged-in doctor's
   * specialty (optional `term` filters by code or name). Codes sent back to the backend must be SCTID.
   */
  getSnomedSuggestions(kind: TerminologyKind, term?: string): Observable<SnomedConceptPage> {
    return this.http.get<SnomedConceptPage>(SNOMED_SUGGESTIONS_URL, {
      params: toHttpParams({ kind, term, size: SUGGESTIONS_SIZE }),
    });
  }
}
