import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { LAB_RESULTS } from '../mock-data/lab-results.mock';
import { PATIENTS } from '../mock-data/patients.mock';
import type {
  AnalyteTrend,
  ID,
  LabResult,
  PatientSummary,
  SelectOption,
  TrendPoint,
} from '../models';
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
export class LabResultService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly results: LabResult[] = structuredClone(LAB_RESULTS);
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

  getRecent(
    filter: 'all' | 'abnormal' | 'critical',
  ): Observable<(LabResult & { patient: PatientSummary })[]> {
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
            return { ...r, patient: patient ? toSummary(patient) : ({} as PatientSummary) };
          }),
      ),
    );
  }
}
