import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { IMAGING_RESULTS } from '../mock-data/imaging-results.mock';
import { PATIENTS } from '../mock-data/patients.mock';
import type { ID, ImagingResult, PatientSummary } from '../models';
import { mockError, mockResponse } from '../utils/mock-response';

function toSummary(p: (typeof PATIENTS)[number]): PatientSummary {
  return {
    id: p.id,
    mrn: p.mrn,
    pesel: p.pesel,
    firstName: p.firstName,
    lastName: p.lastName,
    birthDate: p.birthDate,
    gender: p.gender,
    status: p.status,
    flags: p.flags,
    bed: p.status === 'admitted' ? p.currentAdmission?.bed : undefined,
  };
}

@Injectable({ providedIn: 'root' })
export class ImagingResultService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly results: ImagingResult[] = structuredClone(IMAGING_RESULTS);
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

  getRecent(
    filter: 'all' | 'abnormal' | 'critical',
  ): Observable<(ImagingResult & { patient: PatientSummary })[]> {
    return mockResponse(this.results, this.latency).pipe(
      map((results) =>
        results
          .filter((r) => (filter === 'critical' ? r.critical : true))
          .map((r) => {
            const patient = this.patients.find((p) => p.id === r.patientId);
            return { ...r, patient: patient ? toSummary(patient) : ({} as PatientSummary) };
          }),
      ),
    );
  }
}
