import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import type { Observable } from 'rxjs';
import {
  LAB_RESULTS_URL,
  labResultAcknowledgeUrl,
  labResultUrl,
  patientLabAnalytesUrl,
  patientLabResultsUrl,
  patientLabTrendUrl,
} from '../config/api.config';
import type {
  AnalyteTrend,
  ID,
  LabResult,
  ResultAbnormalityFilter,
  ResultWithPatient,
  SelectOption,
} from '../models';
import type { LabAnalyteRef, Page } from '../models/api';
import { toHttpParams } from '../utils/http-params';
import { MAX_PAGE_SIZE, readAllPages } from '../utils/read-all-pages';

/**
 * Lab results backed by `/lab-results` and `/patients/{id}/lab-results`. Results are read-only
 * here: the backend has no POST (they come from the lab simulator / internal recording service).
 */
@Injectable({ providedIn: 'root' })
export class LabResultService {
  private readonly http = inject(HttpClient);

  /** Patient's results, newest first; `filter` narrows to abnormal/critical ones. */
  getResults(pid: ID, filter?: ResultAbnormalityFilter): Observable<LabResult[]> {
    return this.http.get<LabResult[]>(patientLabResultsUrl(pid), {
      params: toHttpParams({ filter }),
    });
  }

  getResultById(id: ID): Observable<LabResult> {
    return this.http.get<LabResult>(labResultUrl(id));
  }

  /** Idempotent; the backend ignores `version` and takes the reviewer from the token. */
  acknowledgeResult(id: ID): Observable<LabResult> {
    return this.http.post<LabResult>(labResultAcknowledgeUrl(id), {});
  }

  /** Numeric points ascending by collection time; empty `points` for an unknown analyte. */
  getAnalyteTrend(pid: ID, analyteCode: string): Observable<AnalyteTrend> {
    return this.http.get<AnalyteTrend>(patientLabTrendUrl(pid, analyteCode));
  }

  /** Analytes with numeric values for the patient, as select options (value = code). */
  getTrendableAnalytes(pid: ID): Observable<SelectOption[]> {
    return this.http
      .get<LabAnalyteRef[]>(patientLabAnalytesUrl(pid))
      .pipe(map((refs) => refs.map((a) => ({ value: a.code, label: a.name }))));
  }

  /** Inbox: all results with the patient summary, newest first (every page is read). */
  getRecent(filter: ResultAbnormalityFilter): Observable<ResultWithPatient<LabResult>[]> {
    return readAllPages((page) =>
      this.http.get<Page<ResultWithPatient<LabResult>>>(LAB_RESULTS_URL, {
        params: toHttpParams({ filter, page, size: MAX_PAGE_SIZE }),
      }),
    );
  }
}
