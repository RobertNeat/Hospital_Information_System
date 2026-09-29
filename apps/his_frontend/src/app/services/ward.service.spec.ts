import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { WardService } from './ward.service';

describe('WardService', () => {
  let service: WardService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    service = TestBed.inject(WardService);
  });

  it('returns all wards', async () => {
    const wards = await firstValueFrom(service.getWards());
    expect(wards.length).toBeGreaterThan(0);
    expect(wards.some((w) => w.shortName === 'SOR')).toBe(true);
  });

  it('nameOf resolves a known ward id synchronously', () => {
    expect(service.nameOf('ward-sor')).toContain('Ratunkowy');
  });

  it('nameOf falls back to the id for an unknown ward', () => {
    expect(service.nameOf('ward-unknown')).toBe('ward-unknown');
  });
});
