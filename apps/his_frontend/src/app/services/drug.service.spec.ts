import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { DrugService } from './drug.service';

describe('DrugService', () => {
  let service: DrugService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    service = TestBed.inject(DrugService);
  });

  it('search matches by trade name', async () => {
    const results = await firstValueFrom(service.search('Polpril'));
    expect(results.some((d) => d.name === 'Polpril')).toBe(true);
  });

  it('search matches by active substance', async () => {
    const results = await firstValueFrom(service.search('ramipril'));
    expect(results.some((d) => d.activeSubstance === 'Ramipril')).toBe(true);
  });

  it('search matches by ATC code', async () => {
    const results = await firstValueFrom(service.search('J01C'));
    expect(results.some((d) => d.atcCode.startsWith('J01C'))).toBe(true);
  });

  it('getById errors for an unknown id', async () => {
    await expect(firstValueFrom(service.getById('drg-999'))).rejects.toThrow();
  });

  it('checkSafety flags an allergy warning for a J01C drug and a penicillin-allergic patient', async () => {
    const augmentin = await firstValueFrom(service.getById('drg-011')); // Augmentin, J01CR02
    const warnings = await firstValueFrom(service.checkSafety(augmentin, 'pat-001')); // allergic to Penicylina/J01C
    expect(warnings.some((w) => w.type === 'allergy')).toBe(true);
  });

  it('checkSafety returns no warnings for an unrelated drug and patient', async () => {
    const paracetamol = await firstValueFrom(service.getById('drg-014'));
    const warnings = await firstValueFrom(service.checkSafety(paracetamol, 'pat-006'));
    expect(warnings).toEqual([]);
  });
});
