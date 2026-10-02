import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { forkJoin, map, of, switchMap } from 'rxjs';
import type { Observable } from 'rxjs';
import {
  PRESCRIPTIONS_URL,
  patientActiveMedicationsUrl,
  patientPrescriptionsUrl,
  prescriptionCancelUrl,
  prescriptionUrl,
} from '../config/api.config';
import type { ActiveMedication, ID, Prescription } from '../models';
import type {
  Page,
  PrescriptionCancelRequest,
  PrescriptionCreateRequest,
  PrescriptionFilter,
} from '../models/api';
import { toHttpParams } from '../utils/http-params';

/** Backend maximum page size; the list is read page by page into a flat array. */
const MAX_PAGE_SIZE = 100;

/**
 * Prescriptions backed by `/prescriptions`. The backend owns ids, status (including derived
 * expiry), `accessCode` and `eRxKey`. Failures (404/409/422) arrive as `HttpErrorResponse`
 * with a `ProblemDetail` body (see `toApiError`).
 */
@Injectable({ providedIn: 'root' })
export class PrescriptionService {
  private readonly http = inject(HttpClient);

  /** All prescriptions matching the filter, newest first (every page is read). */
  getPrescriptions(filter?: PrescriptionFilter): Observable<Prescription[]> {
    return this.fetchPage(filter, 0).pipe(
      switchMap((first) => {
        if (first.totalPages <= 1) return of(first.items);
        const rest = Array.from({ length: first.totalPages - 1 }, (_, i) =>
          this.fetchPage(filter, i + 1),
        );
        return forkJoin(rest).pipe(map((pages) => [first, ...pages].flatMap((p) => p.items)));
      }),
    );
  }

  getById(id: ID): Observable<Prescription> {
    return this.http.get<Prescription>(prescriptionUrl(id));
  }

  /** Items of the patient's live prescriptions (not expired, not cancelled), newest first. */
  getActiveMedications(pid: ID): Observable<ActiveMedication[]> {
    return this.http.get<ActiveMedication[]>(patientActiveMedicationsUrl(pid));
  }

  /**
   * The prescriber comes from the token. The backend re-reads the prescription after the
   * e-receipt integration runs, so the response already carries the real `eRxKey` for
   * e-prescriptions (falls back to the local key if e-receipt is disabled or unreachable).
   */
  issuePrescription(draft: PrescriptionCreateRequest): Observable<Prescription> {
    return this.http.post<Prescription>(patientPrescriptionsUrl(draft.patientId), draft);
  }

  /** Pass the loaded `version` for optimistic locking; 409 on mismatch or a final status. */
  cancel(id: ID, reason?: string, version?: number): Observable<Prescription> {
    const body: PrescriptionCancelRequest = { reason, version };
    return this.http.post<Prescription>(prescriptionCancelUrl(id), body);
  }

  private fetchPage(
    filter: PrescriptionFilter | undefined,
    page: number,
  ): Observable<Page<Prescription>> {
    return this.http.get<Page<Prescription>>(PRESCRIPTIONS_URL, {
      params: toHttpParams({
        patientId: filter?.patientId,
        prescriberId: filter?.prescriberId,
        status: filter?.status,
        kind: filter?.kind,
        page,
        size: MAX_PAGE_SIZE,
      }),
    });
  }
}
