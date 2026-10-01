import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import { DRUG_SAFETY_CHECKS_URL, DRUGS_URL, drugUrl } from '../config/api.config';
import type { Drug, DrugSafetyWarning, ID } from '../models';
import type { DrugSafetyCheckRequest, DrugSafetyItemRequest } from '../models/api';
import { toHttpParams } from '../utils/http-params';

/**
 * Drug catalog (`/drugs`, read-only, max 50 results) and the backend safety check. Searching
 * needs `drug:read`; the safety check is restricted to doctors (403 otherwise).
 */
@Injectable({ providedIn: 'root' })
export class DrugService {
  private readonly http = inject(HttpClient);

  /** Server-side match of trade name, active substance and ATC code (every token must match). */
  search(term: string): Observable<Drug[]> {
    return this.http.get<Drug[]>(DRUGS_URL, { params: toHttpParams({ term: term.trim() }) });
  }

  getById(id: ID): Observable<Drug> {
    return this.http.get<Drug>(drugUrl(id));
  }

  /**
   * Checks the whole working prescription (allergies, duplicates, interactions, max dose).
   * Warnings are advisory and refer to the checked item by `drugId`; an empty list means none.
   */
  checkSafety(patientId: ID, items: DrugSafetyItemRequest[]): Observable<DrugSafetyWarning[]> {
    const body: DrugSafetyCheckRequest = { patientId, items };
    return this.http.post<DrugSafetyWarning[]>(DRUG_SAFETY_CHECKS_URL, body);
  }
}
