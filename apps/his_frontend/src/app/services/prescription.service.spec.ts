import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import {
  PRESCRIPTIONS_URL,
  patientActiveMedicationsUrl,
  patientPrescriptionsUrl,
  prescriptionCancelUrl,
  prescriptionUrl,
} from '../config/api.config';
import { PRESCRIPTIONS } from '../mock-data/prescriptions.mock';
import type { Prescription, PrescriptionDraft } from '../models';
import type { Page } from '../models/api';
import { PrescriptionService } from './prescription.service';

const page = (items: Prescription[], n: number, totalPages: number): Page<Prescription> => ({
  items,
  page: n,
  size: 100,
  totalElements: items.length,
  totalPages,
});

function draft(): PrescriptionDraft {
  return {
    patientId: 'pat-003',
    prescriberId: 'stf-001',
    validFrom: '2026-01-01',
    validUntil: '2026-01-31',
    kind: 'e_prescription',
    items: PRESCRIPTIONS[0].items,
  };
}

describe('PrescriptionService', () => {
  let service: PrescriptionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PrescriptionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getPrescriptions sends every filter as a query parameter', async () => {
    const result = firstValueFrom(
      service.getPrescriptions({
        patientId: 'pat-001',
        prescriberId: 'stf-001',
        status: 'expired',
        kind: 'hospital_order',
      }),
    );
    const req = http.expectOne((r) => r.url === PRESCRIPTIONS_URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('patientId')).toBe('pat-001');
    expect(req.request.params.get('prescriberId')).toBe('stf-001');
    expect(req.request.params.get('status')).toBe('expired');
    expect(req.request.params.get('kind')).toBe('hospital_order');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('100');
    req.flush(page([PRESCRIPTIONS[0]], 0, 1));
    expect(await result).toEqual([PRESCRIPTIONS[0]]);
  });

  it('getPrescriptions omits absent filters', () => {
    service.getPrescriptions().subscribe();
    const req = http.expectOne((r) => r.url === PRESCRIPTIONS_URL);
    expect(req.request.params.keys().sort()).toEqual(['page', 'size']);
    req.flush(page([], 0, 0));
  });

  it('getPrescriptions reads every page', async () => {
    const result = firstValueFrom(service.getPrescriptions());
    http
      .expectOne((r) => r.url === PRESCRIPTIONS_URL && r.params.get('page') === '0')
      .flush(page([PRESCRIPTIONS[0]], 0, 2));
    http
      .expectOne((r) => r.url === PRESCRIPTIONS_URL && r.params.get('page') === '1')
      .flush(page([PRESCRIPTIONS[1]], 1, 2));
    expect(await result).toEqual([PRESCRIPTIONS[0], PRESCRIPTIONS[1]]);
  });

  it('getById GETs /prescriptions/{id}', async () => {
    const result = firstValueFrom(service.getById('rx-001'));
    http.expectOne({ method: 'GET', url: prescriptionUrl('rx-001') }).flush(PRESCRIPTIONS[0]);
    expect(await result).toEqual(PRESCRIPTIONS[0]);
  });

  it('getActiveMedications GETs the patient projection', async () => {
    const result = firstValueFrom(service.getActiveMedications('pat-001'));
    http.expectOne({ method: 'GET', url: patientActiveMedicationsUrl('pat-001') }).flush([]);
    expect(await result).toEqual([]);
  });

  it('issuePrescription POSTs under the patient and returns the created prescription', async () => {
    const created: Prescription = {
      ...PRESCRIPTIONS[0],
      id: 'new-id',
      patientId: 'pat-003',
      status: 'issued',
    };
    const body = draft();
    const result = firstValueFrom(service.issuePrescription(body));
    const req = http.expectOne({ method: 'POST', url: patientPrescriptionsUrl('pat-003') });
    expect(req.request.body).toEqual(body);
    req.flush(created, { status: 201, statusText: 'Created' });
    expect(await result).toEqual(created);
  });

  it('issuePrescription surfaces a 422 ProblemDetail', async () => {
    const result = firstValueFrom(service.issuePrescription(draft()));
    http.expectOne(patientPrescriptionsUrl('pat-003')).flush(
      {
        type: 'about:blank',
        title: 'Validation failed',
        status: 422,
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'items[0].drugId', message: 'not found', code: 'notFound' }],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    await expect(result).rejects.toMatchObject({ status: 422 });
  });

  it('cancel POSTs reason and version', async () => {
    const result = firstValueFrom(service.cancel('rx-001', 'Błąd w dawkowaniu', 3));
    const req = http.expectOne({ method: 'POST', url: prescriptionCancelUrl('rx-001') });
    expect(req.request.body).toEqual({ reason: 'Błąd w dawkowaniu', version: 3 });
    req.flush({ ...PRESCRIPTIONS[0], status: 'cancelled', cancelReason: 'Błąd w dawkowaniu' });
    expect((await result).status).toBe('cancelled');
  });

  it('cancel surfaces 409 (version or final status)', async () => {
    const result = firstValueFrom(service.cancel('rx-001', undefined, 1));
    http
      .expectOne(prescriptionCancelUrl('rx-001'))
      .flush(null, { status: 409, statusText: 'Conflict' });
    await expect(result).rejects.toMatchObject({ status: 409 });
  });
});
