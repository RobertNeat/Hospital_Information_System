import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { IMAGING_RESULTS } from '../mock-data/imaging-results.mock';
import { PATIENTS } from '../mock-data/patients.mock';
import { WARDS } from '../mock-data/wards.mock';
import type {
  ID,
  ImagingResult,
  PatientSummary,
  ResultAbnormalityFilter,
  ResultWithPatient,
} from '../models';
import { mockError, mockResponse } from '../utils/mock-response';
import { toPatientSummary } from '../utils/patient-summary';
import { StaffService } from './staff.service';

@Injectable({ providedIn: 'root' })
export class ImagingResultService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly results: ImagingResult[] = structuredClone(IMAGING_RESULTS);
  private readonly staffService = inject(StaffService);
  private readonly patients = structuredClone(PATIENTS);

  getResults(pid: ID): Observable<ImagingResult[]> {
    return mockResponse(
      this.results.filter((r) => r.patientId === pid),
      this.latency,
    );
  }

  getResultById(id: ID): Observable<ImagingResult> {
    const found = this.results.find((r) => r.id === id);
    if (!found) return mockError(`Nie znaleziono wyniku o id ${id}`, this.latency);
    return mockResponse(found, this.latency);
  }

  acknowledgeResult(id: ID): Observable<ImagingResult> {
    const index = this.results.findIndex((r) => r.id === id);
    if (index === -1) return mockError(`Nie znaleziono wyniku o id ${id}`, this.latency);
    const updated: ImagingResult = {
      ...this.results[index],
      reviewedAt: new Date().toISOString(),
      reviewedById: this.staffService.currentUser().id,
    };
    this.results[index] = updated;
    return mockResponse(updated, this.latency);
  }

  // mock-only: backend authoritative (patient join is done server-side)
  getRecent(filter: ResultAbnormalityFilter): Observable<ResultWithPatient<ImagingResult>[]> {
    return mockResponse(this.results, this.latency).pipe(
      map((results) =>
        results
          // ImagingResult has no per-finding severity: `critical` is its only abnormality
          // flag, so 'abnormal' and 'critical' both narrow to flagged results.
          .filter((r) => (filter === 'all' ? true : r.critical))
          .map((r) => {
            const patient = this.patients.find((p) => p.id === r.patientId);
            return {
              ...r,
              patient: patient ? toPatientSummary(patient, WARDS) : ({} as PatientSummary),
            };
          }),
      ),
    );
  }
}
