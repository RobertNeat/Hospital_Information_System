import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { forkJoin, map, of, switchMap } from 'rxjs';
import type { Observable } from 'rxjs';
import {
  PATIENTS_URL,
  PATIENT_DUPLICATE_CHECK_URL,
  patientAdmissionsUrl,
  patientDischargeUrl,
  patientUrl,
} from '../config/api.config';
import type {
  Admission,
  AdmissionRecordStatus,
  AdmitPatientRequest,
  DischargeOptions,
  ID,
  Patient,
  PatientDraft,
  PatientSearchQuery,
  PatientSummary,
} from '../models';
import type { Page, PatientUpdateRequest } from '../models/api';
import { toHttpParams } from '../utils/http-params';

/** Backend maximum page size; lists the UI treats as flat arrays are read page by page. */
const MAX_PAGE_SIZE = 100;

/**
 * Patients and admissions backed by `/patients`. The backend owns ids, MRN, status and the
 * admission state machine; mutations return the updated `Patient`.
 */
@Injectable({ providedIn: 'root' })
export class PatientService {
  private readonly http = inject(HttpClient);

  /** All patients matching the query (every page is read; the list is not paged in the UI). */
  getPatients(q?: PatientSearchQuery): Observable<PatientSummary[]> {
    return this.fetchPage(q, 0, MAX_PAGE_SIZE).pipe(
      switchMap((first) => {
        if (first.totalPages <= 1) return of(first.items);
        const rest = Array.from({ length: first.totalPages - 1 }, (_, i) =>
          this.fetchPage(q, i + 1, MAX_PAGE_SIZE),
        );
        return forkJoin(rest).pipe(map((pages) => [first, ...pages].flatMap((p) => p.items)));
      }),
    );
  }

  /** First page of matches for last name, first name, PESEL and MRN (server-side folding). */
  search(term: string): Observable<PatientSummary[]> {
    return this.fetchPage({ term }, 0).pipe(map((page) => page.items));
  }

  getPatientById(id: string): Observable<Patient> {
    return this.http.get<Patient>(patientUrl(id));
  }

  /** Duplicate check by PESEL (sent in the body); `null` when no such patient exists (204). */
  findByPesel(pesel: string): Observable<PatientSummary | null> {
    return this.http
      .post<PatientSummary | null>(PATIENT_DUPLICATE_CHECK_URL, { pesel })
      .pipe(map((match) => match ?? null));
  }

  createPatient(draft: PatientDraft): Observable<Patient> {
    return this.http.post<Patient>(PATIENTS_URL, draft);
  }

  /**
   * PATCH: an absent field is left unchanged, `null` clears it, nested objects are replaced.
   * Pass the loaded `version` for optimistic locking (409 on mismatch).
   */
  updatePatient(id: string, changes: PatientUpdateRequest): Observable<Patient> {
    return this.http.patch<Patient>(patientUrl(id), changes);
  }

  /** Admission history of a patient, newest first. */
  getAdmissions(patientId: ID, status?: AdmissionRecordStatus): Observable<Admission[]> {
    return this.http.get<Admission[]>(patientAdmissionsUrl(patientId), {
      params: toHttpParams({ status }),
    });
  }

  admitPatient(id: string, admission: AdmitPatientRequest): Observable<Patient> {
    return this.http.post<Patient>(patientAdmissionsUrl(id), admission);
  }

  /** `options.version` is the version of the active admission (`currentAdmission.version`). */
  dischargePatient(id: string, at: string, options?: DischargeOptions): Observable<Patient> {
    return this.http.post<Patient>(patientDischargeUrl(id), { dischargedAt: at, ...options });
  }

  private fetchPage(
    q: PatientSearchQuery | undefined,
    page: number,
    size?: number,
  ): Observable<Page<PatientSummary>> {
    return this.http.get<Page<PatientSummary>>(PATIENTS_URL, {
      params: toHttpParams({
        term: q?.term?.trim(),
        status: q?.status,
        wardId: q?.wardId,
        page,
        size,
      }),
    });
  }
}
