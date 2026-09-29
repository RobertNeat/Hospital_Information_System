import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { ImagingResultService } from './imaging-result.service';

describe('ImagingResultService', () => {
  let service: ImagingResultService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    service = TestBed.inject(ImagingResultService);
  });

  it('getResults filters by patientId', async () => {
    const result = await firstValueFrom(service.getResults('pat-007'));
    expect(result.every((r) => r.patientId === 'pat-007')).toBe(true);
  });

  it('getResultById errors for an unknown id', async () => {
    await expect(firstValueFrom(service.getResultById('ires-999'))).rejects.toThrow();
  });

  it('getRecent("critical") only returns critical results', async () => {
    const results = await firstValueFrom(service.getRecent('critical'));
    expect(results.every((r) => r.critical)).toBe(true);
    expect(results.length).toBeGreaterThan(0);
  });
});
