import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { LabResultService } from './lab-result.service';

describe('LabResultService', () => {
  let service: LabResultService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    service = TestBed.inject(LabResultService);
  });

  it('getResults filters by patientId', async () => {
    const result = await firstValueFrom(service.getResults('pat-001'));
    expect(result.every((r) => r.patientId === 'pat-001')).toBe(true);
    expect(result.length).toBeGreaterThan(0);
  });

  it('getResultById errors for an unknown id', async () => {
    await expect(firstValueFrom(service.getResultById('lres-999'))).rejects.toThrow();
  });

  it('getAnalyteTrend builds an ascending points series for HGB', async () => {
    const trend = await firstValueFrom(service.getAnalyteTrend('pat-001', 'HGB'));
    expect(trend.points.length).toBeGreaterThan(1);
    const times = trend.points.map((p) => new Date(p.at).getTime());
    expect([...times].sort((a, b) => a - b)).toEqual(times);
  });

  it('getTrendableAnalytes lists distinct numeric analyte codes for the patient', async () => {
    const options = await firstValueFrom(service.getTrendableAnalytes('pat-001'));
    expect(options.some((o) => o.value === 'HGB')).toBe(true);
  });

  it('getRecent("critical") only returns results with HH/LL flags', async () => {
    const results = await firstValueFrom(service.getRecent('critical'));
    expect(results.length).toBeGreaterThan(0);
    expect(
      results.every((r) => r.observations.some((o) => o.flag === 'HH' || o.flag === 'LL')),
    ).toBe(true);
  });

  it('acknowledgeResult sets reviewedAt and reviewedById', async () => {
    const first = (await firstValueFrom(service.getResults('pat-001')))[0];
    const updated = await firstValueFrom(service.acknowledgeResult(first.id));
    expect(updated.reviewedAt).toBeTruthy();
    expect(updated.reviewedById).toBe('stf-001');
    const stored = await firstValueFrom(service.getResultById(first.id));
    expect(stored.reviewedAt).toBe(updated.reviewedAt);
  });

  it('acknowledgeResult errors for an unknown id', async () => {
    await expect(firstValueFrom(service.acknowledgeResult('lres-999'))).rejects.toThrow();
  });

  it('getRecent includes the patient summary', async () => {
    const results = await firstValueFrom(service.getRecent('all'));
    expect(results[0].patient).toHaveProperty('lastName');
  });
});
