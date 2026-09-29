import { Injectable, inject } from '@angular/core';
import { forkJoin, map } from 'rxjs';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { PATIENTS } from '../mock-data/patients.mock';
import { VITALS } from '../mock-data/vitals.mock';
import type { ID, PatientSummary, VitalSigns, VitalSignsDraft, WardVitalsRow } from '../models';
import { minutesAgo } from '../utils/date-utils';
import { evaluateVitals } from '../utils/vitals-anomaly';
import { mockResponse, nextId } from '../utils/mock-response';
import { TeamMessageService } from './team-message.service';

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

const RANGE_HOURS: Record<'24h' | '7d' | '30d', number> = {
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

  getVitals(pid: ID, range: '24h' | '7d' | '30d' | 'all'): Observable<VitalSigns[]> {
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

  addVitals(
    draft: VitalSignsDraft,
  ): Observable<{ saved: VitalSigns; anomalies: ReturnType<typeof evaluateVitals> }> {
    this.sequence++;
    const saved: VitalSigns = { ...draft, id: nextId('vit', this.sequence) };
    this.vitals.push(saved);
    const anomalies = evaluateVitals(saved);

    const criticalAnomaly = anomalies.find((a) => a.severity === 'critical');
    if (criticalAnomaly) {
      this.teamMessageService
        .pushAlert({
          type: 'vital_anomaly',
          severity: 'critical',
          patientId: saved.patientId,
          message: criticalAnomaly.message,
          link: `/patients/${saved.patientId}/vitals`,
        })
        .subscribe();
    }

    return mockResponse({ saved, anomalies }, this.latency);
  }

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
              patient: toSummary(p),
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
