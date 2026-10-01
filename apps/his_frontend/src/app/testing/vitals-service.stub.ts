import { computed, signal } from '@angular/core';
import { of } from 'rxjs';
import { PATIENTS } from '../mock-data/patients.mock';
import { VITALS } from '../mock-data/vitals.mock';
import { VITAL_THRESHOLDS } from '../mock-data/vital-thresholds.mock';
import { WARDS } from '../mock-data/wards.mock';
import type { VitalAnomaly, VitalSigns, VitalType, VitalsRange, WardVitalsRow } from '../models';
import type { VitalSignsCreateRequest } from '../models/api';
import { VitalsService } from '../services/vitals.service';
import { minutesAgo } from '../utils/date-utils';
import { toPatientSummary } from '../utils/patient-summary';
import { classifyVital } from '../utils/vitals-anomaly';
import { TEST_USER } from './staff-service.stub';

const RANGE_HOURS: Record<Exclude<VitalsRange, 'all'>, number> = {
  '24h': 24,
  '7d': 24 * 7,
  '30d': 24 * 30,
};

const TYPES: VitalType[] = [
  'systolic',
  'diastolic',
  'heartRate',
  'temperature',
  'spo2',
  'respiratoryRate',
];

/** In-memory `VitalsService` double over the mock readings (fresh state per TestBed). */
export function createVitalsServiceStub(): Partial<Record<keyof VitalsService, unknown>> {
  const vitals: VitalSigns[] = structuredClone(VITALS);
  const thresholds = signal(Object.fromEntries(VITAL_THRESHOLDS.map((t) => [t.type, t])));
  const byPatient = (pid: string) =>
    vitals
      .filter((v) => v.patientId === pid)
      .sort((a, b) => a.recordedAt.localeCompare(b.recordedAt));
  const anomaliesOf = (v: VitalSigns): VitalAnomaly[] =>
    TYPES.flatMap((type) => {
      const c = classifyVital(thresholds(), type, v[type]);
      return c
        ? [
            {
              type,
              value: v[type] as number,
              ...c,
              message: `${type}: anomalia`,
              recordedAt: v.recordedAt,
            },
          ]
        : [];
    });

  return {
    thresholds: computed(() => thresholds()),
    loadThresholds: () => of(VITAL_THRESHOLDS),
    getVitals: (pid: string, range: VitalsRange) => {
      const cutoff = range === 'all' ? 0 : Date.now() - RANGE_HOURS[range] * 3600_000;
      return of(byPatient(pid).filter((v) => new Date(v.recordedAt).getTime() >= cutoff));
    },
    getLatest: (pid: string) => of(byPatient(pid).at(-1)),
    addVitals: (draft: VitalSignsCreateRequest) => {
      const saved: VitalSigns = {
        ...draft,
        id: `test-vit-${vitals.length + 1}`,
        recordedAt: draft.recordedAt ?? new Date().toISOString(),
        recordedById: TEST_USER.id,
      };
      vitals.push(saved);
      return of({ saved, anomalies: anomaliesOf(saved) });
    },
    getWardOverview: (wardId?: string) => {
      const rows: WardVitalsRow[] = PATIENTS.filter(
        (p) => p.status === 'admitted' && (!wardId || p.currentAdmission?.wardId === wardId),
      ).map((p) => {
        const latest = byPatient(p.id).at(-1);
        return {
          patient: toPatientSummary(p, WARDS),
          latest,
          anomalies: latest ? anomaliesOf(latest) : [],
          lastMeasuredAgoMin: latest ? minutesAgo(latest.recordedAt) : undefined,
        };
      });
      const rank = (r: WardVitalsRow) =>
        r.anomalies.some((a) => a.severity === 'critical') ? 0 : r.anomalies.length > 0 ? 1 : 2;
      return of(rows.sort((a, b) => rank(a) - rank(b)));
    },
  };
}

/** Test provider replacing the HTTP-backed `VitalsService` with the mock readings. */
export const vitalsServiceStub = {
  provide: VitalsService,
  useFactory: createVitalsServiceStub,
};
