import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { finalize, map, of, shareReplay, tap } from 'rxjs';
import type { Observable } from 'rxjs';
import {
  VITALS_WARD_OVERVIEW_URL,
  VITAL_THRESHOLDS_URL,
  patientVitalsLatestUrl,
  patientVitalsUrl,
} from '../config/api.config';
import type {
  ID,
  VitalSigns,
  VitalThreshold,
  VitalType,
  VitalsRange,
  VitalsRecordResponse,
  WardVitalsRow,
} from '../models';
import type { VitalSignsCreateRequest } from '../models/api';
import { toHttpParams } from '../utils/http-params';

/**
 * Vital signs backed by `/patients/{id}/vitals`, `/vitals/ward-overview` and `/vital-thresholds`.
 * Anomalies (on save and in the ward overview) and the ward projection are computed by the
 * backend. Readings are immutable (a correction is a new reading). Failures arrive as
 * `HttpErrorResponse` with a `ProblemDetail` body; a 422 on save has `errors[].field` equal to
 * the measurement name (`range`, `invalidFormat`, `required` for `measurements`).
 */
@Injectable({ providedIn: 'root' })
export class VitalsService {
  private readonly http = inject(HttpClient);

  private readonly thresholdCache = signal<VitalThreshold[] | null>(null);
  private inflightThresholds: Observable<VitalThreshold[]> | undefined;

  /** Thresholds by type (empty until `loadThresholds()` completes). */
  readonly thresholds = computed<Partial<Record<VitalType, VitalThreshold>>>(() =>
    Object.fromEntries((this.thresholdCache() ?? []).map((t) => [t.type, t])),
  );

  /** `GET /vital-thresholds`, fetched once and cached (read-only configuration). */
  loadThresholds(): Observable<VitalThreshold[]> {
    const cached = this.thresholdCache();
    if (cached) return of(cached);
    this.inflightThresholds ??= this.http.get<VitalThreshold[]>(VITAL_THRESHOLDS_URL).pipe(
      tap((list) => this.thresholdCache.set(list)),
      finalize(() => (this.inflightThresholds = undefined)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.inflightThresholds;
  }

  /** Readings of a patient, oldest first. */
  getVitals(pid: ID, range: VitalsRange): Observable<VitalSigns[]> {
    return this.http.get<VitalSigns[]>(patientVitalsUrl(pid), {
      params: toHttpParams({ range }),
    });
  }

  /** The newest reading; `undefined` when the patient has none (the backend answers 204). */
  getLatest(pid: ID): Observable<VitalSigns | undefined> {
    return this.http
      .get<VitalSigns | null>(patientVitalsLatestUrl(pid))
      .pipe(map((latest) => latest ?? undefined));
  }

  /** Saves a reading; the server assigns the id, the actor and the anomalies. */
  addVitals(draft: VitalSignsCreateRequest): Observable<VitalsRecordResponse> {
    return this.http.post<VitalsRecordResponse>(patientVitalsUrl(draft.patientId), draft);
  }

  /** Admitted patients with their latest reading and anomalies, worst first (server order). */
  getWardOverview(wardId?: ID): Observable<WardVitalsRow[]> {
    return this.http.get<WardVitalsRow[]>(VITALS_WARD_OVERVIEW_URL, {
      params: toHttpParams({ wardId }),
    });
  }
}
