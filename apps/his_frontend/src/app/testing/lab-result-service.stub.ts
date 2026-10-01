import { of, throwError } from 'rxjs';
import { LAB_RESULTS } from '../mock-data/lab-results.mock';
import { PATIENTS } from '../mock-data/patients.mock';
import { WARDS } from '../mock-data/wards.mock';
import type {
  LabResult,
  PatientSummary,
  ResultAbnormalityFilter,
  SelectOption,
  TrendPoint,
} from '../models';
import { LabResultService } from '../services/lab-result.service';
import { toPatientSummary } from '../utils/patient-summary';
import { TEST_USER } from './staff-service.stub';

const matches = (r: LabResult, filter?: ResultAbnormalityFilter): boolean => {
  if (!filter || filter === 'all') return true;
  const flags = r.observations.map((o) => o.flag);
  return filter === 'critical'
    ? flags.some((f) => f === 'HH' || f === 'LL')
    : flags.some((f) => f !== 'N');
};

/** In-memory `LabResultService` double over the mock lab results (fresh state per TestBed). */
export function createLabResultServiceStub(): Partial<Record<keyof LabResultService, unknown>> {
  const results: LabResult[] = structuredClone(LAB_RESULTS);
  const notFound = (id: string) => throwError(() => new Error(`Nie znaleziono wyniku ${id}`));

  return {
    getResults: (pid: string, filter?: ResultAbnormalityFilter) =>
      of(results.filter((r) => r.patientId === pid && matches(r, filter))),
    getResultById: (id: string) => {
      const found = results.find((r) => r.id === id);
      return found ? of(found) : notFound(id);
    },
    acknowledgeResult: (id: string) => {
      const index = results.findIndex((r) => r.id === id);
      if (index === -1) return notFound(id);
      results[index] = {
        ...results[index],
        reviewedAt: new Date().toISOString(),
        reviewedById: TEST_USER.id,
      };
      return of(results[index]);
    },
    getAnalyteTrend: (pid: string, analyteCode: string) => {
      const points: TrendPoint[] = [];
      let analyteName = analyteCode;
      let unit = '';
      const own = results
        .filter((r) => r.patientId === pid)
        .sort((a, b) => a.collectedAt.localeCompare(b.collectedAt));
      for (const r of own) {
        const obs = r.observations.find((o) => o.analyteCode === analyteCode);
        if (!obs || typeof obs.value !== 'number') continue;
        analyteName = obs.analyteName;
        unit = obs.unit;
        points.push({ at: r.collectedAt, value: obs.value, flag: obs.flag });
      }
      return of({ analyteCode, analyteName, unit, points });
    },
    getTrendableAnalytes: (pid: string) => {
      const codes = new Map<string, string>();
      for (const r of results.filter((x) => x.patientId === pid)) {
        for (const o of r.observations) {
          if (typeof o.value === 'number') codes.set(o.analyteCode, o.analyteName);
        }
      }
      return of<SelectOption[]>([...codes].map(([value, label]) => ({ value, label })));
    },
    getRecent: (filter: ResultAbnormalityFilter) =>
      of(
        results
          .filter((r) => matches(r, filter))
          .map((r) => {
            const patient = PATIENTS.find((p) => p.id === r.patientId);
            return {
              ...r,
              patient: patient ? toPatientSummary(patient, WARDS) : ({} as PatientSummary),
            };
          }),
      ),
  };
}

/** Test provider replacing the HTTP-backed `LabResultService` with the mock lab results. */
export const labResultServiceStub = {
  provide: LabResultService,
  useFactory: createLabResultServiceStub,
};
