import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import {
  PATIENTS_URL,
  PATIENT_DUPLICATE_CHECK_URL,
  patientAdmissionsUrl,
  patientDischargeUrl,
  patientUrl,
} from '../config/api.config';
import type { Patient, PatientDraft, PatientSummary } from '../models';
import type { Page } from '../models/api';
import { PatientService } from './patient.service';

const SUMMARY: PatientSummary = {
  id: 'p-1',
  mrn: 'HIS/2026/000001',
  pesel: null,
  firstName: 'Jan',
  lastName: 'Kowalski',
  birthDate: '1980-01-01',
  gender: 'male',
  status: 'registered',
  flags: [],
};

const page = (items: PatientSummary[], p = 0, totalPages = 1): Page<PatientSummary> => ({
  items,
  page: p,
  size: 100,
  totalElements: items.length,
  totalPages,
});

describe('PatientService', () => {
  let service: PatientService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PatientService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getPatients sends the filters and unwraps the page', async () => {
    const result = firstValueFrom(
      service.getPatients({ term: ' kowal ', status: 'admitted', wardId: 'w-1' }),
    );
    const req = http.expectOne((r) => r.url === PATIENTS_URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('term')).toBe('kowal');
    expect(req.request.params.get('status')).toBe('admitted');
    expect(req.request.params.get('wardId')).toBe('w-1');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('100');
    req.flush(page([SUMMARY]));
    expect(await result).toEqual([SUMMARY]);
  });

  it('getPatients reads the remaining pages', async () => {
    const other = { ...SUMMARY, id: 'p-2' };
    const result = firstValueFrom(service.getPatients());
    http
      .expectOne((r) => r.url === PATIENTS_URL && r.params.get('page') === '0')
      .flush(page([SUMMARY], 0, 2));
    http
      .expectOne((r) => r.url === PATIENTS_URL && r.params.get('page') === '1')
      .flush(page([other], 1, 2));
    expect((await result).map((p) => p.id)).toEqual(['p-1', 'p-2']);
  });

  it('search reads a single default-size page', async () => {
    const result = firstValueFrom(service.search('kow'));
    const req = http.expectOne((r) => r.url === PATIENTS_URL);
    expect(req.request.params.get('term')).toBe('kow');
    expect(req.request.params.has('size')).toBe(false);
    req.flush(page([SUMMARY]));
    expect(await result).toEqual([SUMMARY]);
  });

  it('getPatientById GETs one patient and propagates a 404', async () => {
    const ok = firstValueFrom(service.getPatientById('p-1'));
    http.expectOne({ method: 'GET', url: patientUrl('p-1') }).flush({ id: 'p-1' });
    expect((await ok).id).toBe('p-1');

    const missing = firstValueFrom(service.getPatientById('x'));
    http.expectOne(patientUrl('x')).flush(null, { status: 404, statusText: 'Not Found' });
    await expect(missing).rejects.toBeTruthy();
  });

  it('findByPesel POSTs the PESEL in the body; 204 means no duplicate', async () => {
    const hit = firstValueFrom(service.findByPesel('80010100000'));
    const req = http.expectOne({ method: 'POST', url: PATIENT_DUPLICATE_CHECK_URL });
    expect(req.request.body).toEqual({ pesel: '80010100000' });
    req.flush(SUMMARY);
    expect(await hit).toEqual(SUMMARY);

    const none = firstValueFrom(service.findByPesel('80010100001'));
    http
      .expectOne(PATIENT_DUPLICATE_CHECK_URL)
      .flush(null, { status: 204, statusText: 'No Content' });
    expect(await none).toBeNull();
  });

  it('createPatient POSTs the draft and returns the backend patient', async () => {
    const draft = { firstName: 'Test' } as PatientDraft;
    const result = firstValueFrom(service.createPatient(draft));
    const req = http.expectOne({ method: 'POST', url: PATIENTS_URL });
    expect(req.request.body).toEqual(draft);
    req.flush({ id: 'p-9', mrn: 'HIS/2026/000009' } as Patient, {
      status: 201,
      statusText: 'Created',
    });
    expect((await result).mrn).toBe('HIS/2026/000009');
  });

  it('createPatient surfaces a 409 duplicate PESEL', async () => {
    const result = firstValueFrom(service.createPatient({} as PatientDraft));
    http
      .expectOne(PATIENTS_URL)
      .flush({ status: 409, title: 'Conflict' }, { status: 409, statusText: 'Conflict' });
    await expect(result).rejects.toMatchObject({ status: 409 });
  });

  it('updatePatient PATCHes the changes with null clearing and the version', async () => {
    const changes = { phone: null, version: 3 };
    const result = firstValueFrom(service.updatePatient('p-1', changes));
    const req = http.expectOne({ method: 'PATCH', url: patientUrl('p-1') });
    expect(req.request.body).toEqual(changes);
    req.flush({ id: 'p-1', version: 4 });
    expect((await result).version).toBe(4);
  });

  it('getAdmissions GETs the history, optionally by status', async () => {
    const result = firstValueFrom(service.getAdmissions('p-1', 'active'));
    const req = http.expectOne((r) => r.url === patientAdmissionsUrl('p-1'));
    expect(req.request.params.get('status')).toBe('active');
    req.flush([{ id: 'a-1' }]);
    expect(await result).toHaveLength(1);
  });

  it('admitPatient POSTs the admission and returns the updated patient', async () => {
    const admission = {
      admissionType: 'planned',
      admittedAt: '2026-01-01T10:00:00Z',
      wardId: 'w-1',
      attendingPhysicianId: 's-1',
      reason: 'Test',
    } as const;
    const result = firstValueFrom(service.admitPatient('p-1', admission));
    const req = http.expectOne({ method: 'POST', url: patientAdmissionsUrl('p-1') });
    expect(req.request.body).toEqual(admission);
    req.flush({ id: 'p-1', status: 'admitted' }, { status: 201, statusText: 'Created' });
    expect((await result).status).toBe('admitted');
  });

  it('dischargePatient POSTs dischargedAt with the options and the admission version', async () => {
    const result = firstValueFrom(
      service.dischargePatient('p-1', '2026-01-05T10:00:00Z', { disposition: 'home', version: 2 }),
    );
    const req = http.expectOne({ method: 'POST', url: patientDischargeUrl('p-1') });
    expect(req.request.body).toEqual({
      dischargedAt: '2026-01-05T10:00:00Z',
      disposition: 'home',
      version: 2,
    });
    req.flush({ id: 'p-1', status: 'discharged' });
    expect((await result).status).toBe('discharged');
  });
});
