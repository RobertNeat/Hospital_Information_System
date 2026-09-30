import { Injectable, inject } from '@angular/core';
import { forkJoin, map } from 'rxjs';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { PATIENTS } from '../mock-data/patients.mock';
import { WARDS } from '../mock-data/wards.mock';
import { VITALS } from '../mock-data/vitals.mock';
import type {
  ID,
  VitalSigns,
  VitalSignsDraft,
  VitalsRange,
  VitalsRecordResponse,
  WardVitalsRow,
} from '../models';
import { minutesAgo } from '../utils/date-utils';
import { evaluateVitals } from '../utils/vitals-anomaly';
import { mockResponse, nextId } from '../utils/mock-response';
import { toPatientSummary } from '../utils/patient-summary';
import { TeamMessageService } from './team-message.service';

const RANGE_HOURS: Record<Exclude<VitalsRange, 'all'>, number> = {
  '24h': 24,
  '7d': 24 * 7,
  '30d': 24 * 30,
};

@Injectable({ providedIn: 'root' })
export class VitalsService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly teamMessageService = inject(TeamMessageService);
  private readonly vitals: VitalSigns[] = structuredClone(VITALS);
  private readonly patients = structuredClone(PATIENTS);
  private sequence = this.vitals.length;

  getVitals(pid: ID, range: VitalsRange): Observable<VitalSigns[]> {
    let result = this.vitals.filter((v) => v.patientId === pid);
    if (range !== 'all') {
      const cutoffMs = Date.now() - RANGE_HOURS[range] * 3600_000;
      result = result.filter((v) => new Date(v.recordedAt).getTime() >= cutoffMs);
    }
    result = [...result].sort((a, b) => a.recordedAt.localeCompare(b.recordedAt));
    return mockResponse(result, this.latency);
  }

  getLatest(pid: ID): Observable<VitalSigns | undefined> {
    const patientVitals = this.vitals
      .filter((v) => v.patientId === pid)
      .sort((a, b) => b.recordedAt.localeCompare(a.recordedAt));
    return mockResponse(patientVitals[0], this.latency);
  }

  addVitals(draft: VitalSignsDraft): Observable<VitalsRecordResponse> {
    this.sequence++;
    const saved: VitalSigns = { ...draft, id: nextId('vit', this.sequence) };
    this.vitals.push(saved);
    // mock-only: backend authoritative (anomalies are computed server-side)
    const anomalies = evaluateVitals(saved);

    const criticalAnomaly = anomalies.find((a) => a.severity === 'critical');
    if (criticalAnomaly) {
      this.teamMessageService
        .pushAlert({
          type: 'vital_anomaly',
          severity: 'critical',
          patientId: saved.patientId,
          message: criticalAnomaly.message,
          target: { kind: 'patient_vitals', id: saved.patientId },
          link: `/patients/${saved.patientId}/vitals`,
        })
        .subscribe();
    }

    return mockResponse({ saved, anomalies }, this.latency);
  }

  // mock-only: backend authoritative (ward projection and anomalies are computed server-side)
  getWardOverview(wardId?: ID): Observable<WardVitalsRow[]> {
    // A discharged patient can still carry a stale currentAdmission.wardId, so always
    // require status === 'admitted' in addition to any ward filter.
    const patients = wardId
      ? this.patients.filter(
          (p) => p.status === 'admitted' && p.currentAdmission?.wardId === wardId,
        )
      : this.patients.filter((p) => p.status === 'admitted');

    return forkJoin(
      patients.map((p) =>
        this.getLatest(p.id).pipe(
          map((latest) => {
            const anomalies = latest ? evaluateVitals(latest) : [];
            return {
              patient: toPatientSummary(p, WARDS),
              latest,
              anomalies,
              lastMeasuredAgoMin: latest ? minutesAgo(latest.recordedAt) : undefined,
            } satisfies WardVitalsRow;
          }),
        ),
      ),
    ).pipe(
      map((rows) => {
        const severityRank = (row: WardVitalsRow): number =>
          row.anomalies.some((a) => a.severity === 'critical')
            ? 0
            : row.anomalies.length > 0
              ? 1
              : 2;
        return [...rows].sort((a, b) => severityRank(a) - severityRank(b));
      }),
    );
  }
}
