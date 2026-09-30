import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { LAB_RESULTS } from '../mock-data/lab-results.mock';
import { PATIENTS } from '../mock-data/patients.mock';
import { WARDS } from '../mock-data/wards.mock';
import type {
  AnalyteTrend,
  ID,
  LabResult,
  PatientSummary,
  ResultAbnormalityFilter,
  ResultWithPatient,
  SelectOption,
  TrendPoint,
} from '../models';
import { mockError, mockResponse } from '../utils/mock-response';
import { toPatientSummary } from '../utils/patient-summary';
import { StaffService } from './staff.service';

@Injectable({ providedIn: 'root' })
export class LabResultService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly results: LabResult[] = structuredClone(LAB_RESULTS);
  private readonly staffService = inject(StaffService);
  private readonly patients = structuredClone(PATIENTS);

  getResults(pid: ID): Observable<LabResult[]> {
    return mockResponse(
      this.results.filter((r) => r.patientId === pid),
      this.latency,
    );
  }

  getResultById(id: ID): Observable<LabResult> {
    const found = this.results.find((r) => r.id === id);
    if (!found) return mockError(`Nie znaleziono wyniku o id ${id}`, this.latency);
    return mockResponse(found, this.latency);
  }

  acknowledgeResult(id: ID): Observable<LabResult> {
    const index = this.results.findIndex((r) => r.id === id);
    if (index === -1) return mockError(`Nie znaleziono wyniku o id ${id}`, this.latency);
    const updated: LabResult = {
      ...this.results[index],
      reviewedAt: new Date().toISOString(),
      reviewedById: this.staffService.currentUser().id,
    };
    this.results[index] = updated;
    return mockResponse(updated, this.latency);
  }

  // mock-only: backend authoritative (trend computed server-side)
  getAnalyteTrend(pid: ID, analyteCode: string): Observable<AnalyteTrend> {
    const patientResults = this.results
      .filter((r) => r.patientId === pid)
      .sort((a, b) => a.collectedAt.localeCompare(b.collectedAt));

    let analyteName = analyteCode;
    let unit = '';
    let low: number | undefined;
    let high: number | undefined;
    const points: TrendPoint[] = [];

    for (const result of patientResults) {
      const obs = result.observations.find((o) => o.analyteCode === analyteCode);
      if (!obs || typeof obs.value !== 'number') continue;
      analyteName = obs.analyteName;
      unit = obs.unit;
      low = obs.referenceRange.low;
      high = obs.referenceRange.high;
      points.push({ at: result.collectedAt, value: obs.value, flag: obs.flag });
    }

    return mockResponse({ analyteCode, analyteName, unit, low, high, points }, this.latency);
  }

  /** UI-typed options; backend returns the patient's analyte list (code + name), mapped to `SelectOption` client-side. */
  // mock-only: backend authoritative
  getTrendableAnalytes(pid: ID): Observable<SelectOption[]> {
    const codes = new Map<string, string>();
    for (const r of this.results) {
      if (r.patientId !== pid) continue;
      for (const o of r.observations) {
        if (typeof o.value === 'number' && !codes.has(o.analyteCode)) {
          codes.set(o.analyteCode, o.analyteName);
        }
      }
    }
    const options: SelectOption[] = Array.from(codes.entries()).map(([value, label]) => ({
      value,
      label,
    }));
    return mockResponse(options, this.latency);
  }

  // mock-only: backend authoritative (patient join is done server-side)
  getRecent(filter: ResultAbnormalityFilter): Observable<ResultWithPatient<LabResult>[]> {
    return mockResponse(this.results, this.latency).pipe(
      map((results) =>
        results
          .filter((r) => {
            if (filter === 'all') return true;
            const flags = r.observations.map((o) => o.flag);
            if (filter === 'critical') return flags.some((f) => f === 'HH' || f === 'LL');
            return flags.some((f) => f !== 'N');
          })
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
