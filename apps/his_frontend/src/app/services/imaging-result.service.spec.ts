import { staffServiceStub } from '../testing/staff-service.stub';
import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { ImagingResultService } from './imaging-result.service';

describe('ImagingResultService', () => {
  let service: ImagingResultService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [staffServiceStub, { provide: MOCK_LATENCY_MS, useValue: 0 }],
    });
    service = TestBed.inject(ImagingResultService);
  });

  it('getResults filters by patientId', async () => {
    const result = await firstValueFrom(service.getResults('pat-007'));
    expect(result.every((r) => r.patientId === 'pat-007')).toBe(true);
  });

  it('getResultById errors for an unknown id', async () => {
    await expect(firstValueFrom(service.getResultById('ires-999'))).rejects.toThrow();
  });

  it('acknowledgeResult sets reviewedAt and reviewedById', async () => {
    const updated = await firstValueFrom(service.acknowledgeResult('ires-001'));
    expect(updated.reviewedAt).toBeTruthy();
    expect(updated.reviewedById).toBeTruthy();
    const fetched = await firstValueFrom(service.getResultById('ires-001'));
    expect(fetched.reviewedAt).toBe(updated.reviewedAt);
  });

  it('acknowledgeResult errors for an unknown id', async () => {
    await expect(firstValueFrom(service.acknowledgeResult('ires-999'))).rejects.toThrow();
  });

  it('getRecent("critical") only returns critical results', async () => {
    const results = await firstValueFrom(service.getRecent('critical'));
    expect(results.every((r) => r.critical)).toBe(true);
    expect(results.length).toBeGreaterThan(0);
  });

  it('getRecent("abnormal") narrows to flagged results, "all" returns everything', async () => {
    const [all, abnormal] = await Promise.all([
      firstValueFrom(service.getRecent('all')),
      firstValueFrom(service.getRecent('abnormal')),
    ]);
    expect(abnormal.length).toBeGreaterThan(0);
    expect(abnormal.length).toBeLessThan(all.length);
    expect(abnormal.every((r) => r.critical)).toBe(true);
  });
});
