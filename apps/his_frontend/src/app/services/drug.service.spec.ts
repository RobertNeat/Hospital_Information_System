import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { DRUG_SAFETY_CHECKS_URL, DRUGS_URL, drugUrl } from '../config/api.config';
import { DRUGS } from '../mock-data/drugs.mock';
import type { DrugSafetyWarning } from '../models';
import { DrugService } from './drug.service';

describe('DrugService', () => {
  let service: DrugService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DrugService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('search GETs /drugs with the trimmed term', async () => {
    const result = firstValueFrom(service.search(' ramipril '));
    const req = http.expectOne((r) => r.url === DRUGS_URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('term')).toBe('ramipril');
    req.flush([DRUGS[0]]);
    expect(await result).toEqual([DRUGS[0]]);
  });

  it('search omits an empty term', () => {
    service.search('  ').subscribe();
    const req = http.expectOne((r) => r.url === DRUGS_URL);
    expect(req.request.params.has('term')).toBe(false);
    req.flush([]);
  });

  it('getById GETs /drugs/{id}', async () => {
    const result = firstValueFrom(service.getById('drg-001'));
    http.expectOne({ method: 'GET', url: drugUrl('drg-001') }).flush(DRUGS[0]);
    expect(await result).toEqual(DRUGS[0]);
  });

  it('getById surfaces 404 as an error', async () => {
    const result = firstValueFrom(service.getById('drg-999'));
    http.expectOne(drugUrl('drg-999')).flush(null, { status: 404, statusText: 'Not Found' });
    await expect(result).rejects.toMatchObject({ status: 404 });
  });

  it('checkSafety POSTs the whole working prescription as items[]', async () => {
    const warnings: DrugSafetyWarning[] = [
      { type: 'allergy', severity: 'danger', drugId: 'drg-011', message: 'Alergia' },
    ];
    const items = [
      { drugId: 'drg-001' },
      {
        drugId: 'drg-011',
        dosage: {
          dose: 500,
          doseUnit: 'mg',
          route: 'oral' as const,
          frequency: 'TID' as const,
          durationDays: 5,
          asNeeded: false,
        },
      },
    ];
    const result = firstValueFrom(service.checkSafety('pat-001', items));
    const req = http.expectOne({ method: 'POST', url: DRUG_SAFETY_CHECKS_URL });
    expect(req.request.body).toEqual({ patientId: 'pat-001', items });
    req.flush(warnings);
    expect(await result).toEqual(warnings);
  });

  it('checkSafety surfaces 403 (non-doctor) as an error', async () => {
    const result = firstValueFrom(service.checkSafety('pat-001', [{ drugId: 'drg-001' }]));
    http.expectOne(DRUG_SAFETY_CHECKS_URL).flush(null, { status: 403, statusText: 'Forbidden' });
    await expect(result).rejects.toMatchObject({ status: 403 });
  });
});
