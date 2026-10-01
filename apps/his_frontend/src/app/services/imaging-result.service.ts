import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import {
  IMAGING_RESULTS_URL,
  imagingResultAcknowledgeUrl,
  imagingResultUrl,
  patientImagingResultsUrl,
} from '../config/api.config';
import type { ID, ImagingResult, ResultAbnormalityFilter, ResultWithPatient } from '../models';
import type { Page } from '../models/api';
import { toHttpParams } from '../utils/http-params';
import { MAX_PAGE_SIZE, readAllPages } from '../utils/read-all-pages';

/**
 * Imaging results backed by `/imaging-results` and `/patients/{id}/imaging-results`. Read-only:
 * the backend has no POST (results come from the imaging simulator / internal recording service).
 * `critical` is the only abnormality flag, so `abnormal` and `critical` filters are equivalent.
 */
@Injectable({ providedIn: 'root' })
export class ImagingResultService {
  private readonly http = inject(HttpClient);

  /** Patient's results, newest first; `filter` narrows to critical ones. */
  getResults(pid: ID, filter?: ResultAbnormalityFilter): Observable<ImagingResult[]> {
    return this.http.get<ImagingResult[]>(patientImagingResultsUrl(pid), {
      params: toHttpParams({ filter }),
    });
  }

  getResultById(id: ID): Observable<ImagingResult> {
    return this.http.get<ImagingResult>(imagingResultUrl(id));
  }

  /** Idempotent; the backend ignores `version` and takes the reviewer from the token. */
  acknowledgeResult(id: ID): Observable<ImagingResult> {
    return this.http.post<ImagingResult>(imagingResultAcknowledgeUrl(id), {});
  }

  /** Inbox: all results with the patient summary, newest first (every page is read). */
  getRecent(filter: ResultAbnormalityFilter): Observable<ResultWithPatient<ImagingResult>[]> {
    return readAllPages((page) =>
      this.http.get<Page<ResultWithPatient<ImagingResult>>>(IMAGING_RESULTS_URL, {
        params: toHttpParams({ filter, page, size: MAX_PAGE_SIZE }),
      }),
    );
  }
}
