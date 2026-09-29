import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import type { VitalSignsDraft } from '../models';
import { VitalsService } from './vitals.service';
import { TeamMessageService } from './team-message.service';

describe('VitalsService', () => {
  let service: VitalsService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    service = TestBed.inject(VitalsService);
  });

  it('getVitals("all") returns ascending-sorted readings for the patient', async () => {
    const vitals = await firstValueFrom(service.getVitals('pat-001', 'all'));
    expect(vitals.length).toBeGreaterThan(0);
    const times = vitals.map((v) => new Date(v.recordedAt).getTime());
    expect([...times].sort((a, b) => a - b)).toEqual(times);
  });

  it('getVitals("24h") only returns readings within the last 24 hours', async () => {
    const vitals = await firstValueFrom(service.getVitals('pat-001', '24h'));
    const cutoff = Date.now() - 24 * 3600_000;
    expect(vitals.every((v) => new Date(v.recordedAt).getTime() >= cutoff)).toBe(true);
  });

  it('getLatest returns the most recent reading', async () => {
    const all = await firstValueFrom(service.getVitals('pat-001', 'all'));
    const latest = await firstValueFrom(service.getLatest('pat-001'));
    expect(latest?.id).toBe(all.at(-1)?.id);
  });

  it('addVitals persists the reading and returns computed anomalies', async () => {
    const before = await firstValueFrom(service.getVitals('pat-003', 'all'));
    const draft: VitalSignsDraft = {
      patientId: 'pat-003',
      recordedAt: new Date().toISOString(),
      recordedById: 'stf-001',
      context: 'office_exam',
      systolic: 150,
      diastolic: 95,
    };
    const { saved, anomalies } = await firstValueFrom(service.addVitals(draft));
    expect(saved.id).toMatch(/^vit-\d+$/);
    expect(anomalies.some((a) => a.type === 'systolic')).toBe(true);

    const after = await firstValueFrom(service.getVitals('pat-003', 'all'));
    expect(after.length).toBe(before.length + 1);
  });

  it('addVitals pushes a critical alert via TeamMessageService when an anomaly is critical', async () => {
    const teamMessageService = TestBed.inject(TeamMessageService);
    const before = await firstValueFrom(teamMessageService.getAlerts({ patientId: 'pat-003' }));

    await firstValueFrom(
      service.addVitals({
        patientId: 'pat-003',
        recordedAt: new Date().toISOString(),
        recordedById: 'stf-001',
        context: 'office_exam',
        spo2: 85,
      }),
    );

    const after = await firstValueFrom(teamMessageService.getAlerts({ patientId: 'pat-003' }));
    expect(after.length).toBe(before.length + 1);
    expect(after[0].severity).toBe('critical');
  });

  it('getWardOverview excludes discharged patients with a stale currentAdmission.wardId', async () => {
    // pat-005 is discharged but its mock currentAdmission still has wardId 'ward-int'.
    const rows = await firstValueFrom(service.getWardOverview('ward-int'));
    expect(rows.some((r) => r.patient.id === 'pat-005')).toBe(false);
  });

  it('getWardOverview sorts critical anomalies first', async () => {
    const rows = await firstValueFrom(service.getWardOverview());
    expect(rows.length).toBeGreaterThan(0);
    const ranks = rows.map((r) =>
      r.anomalies.some((a) => a.severity === 'critical') ? 0 : r.anomalies.length > 0 ? 1 : 2,
    );
    expect([...ranks].sort((a, b) => a - b)).toEqual(ranks);
  });
});
