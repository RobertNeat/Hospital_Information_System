import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import type { PatientDraft } from '../models';
import { PatientService } from './patient.service';

function draft(): PatientDraft {
  return {
    pesel: null,
    noPeselReason: 'newborn',
    firstName: 'Test',
    lastName: 'Testowy',
    birthDate: '2026-01-01',
    gender: 'male',
    address: {
      street: 'Testowa',
      buildingNumber: '1',
      postalCode: '00-001',
      city: 'Warszawa',
      country: 'Polska',
    },
    insurance: { status: 'unknown', nfzBranch: '07', payer: 'none' },
    status: 'registered',
    flags: [],
  };
}

describe('PatientService', () => {
  let service: PatientService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    service = TestBed.inject(PatientService);
  });

  it('returns patient summaries', async () => {
    const patients = await firstValueFrom(service.getPatients());
    expect(patients.length).toBeGreaterThan(0);
    expect(patients[0]).toHaveProperty('mrn');
  });

  it('filters by status', async () => {
    const admitted = await firstValueFrom(service.getPatients({ status: 'admitted' }));
    expect(admitted.every((p) => p.status === 'admitted')).toBe(true);
  });

  it('filtering by wardId excludes discharged patients with a stale currentAdmission.wardId', async () => {
    // pat-005 is discharged but its mock currentAdmission still has wardId 'ward-int'.
    const result = await firstValueFrom(service.getPatients({ wardId: 'ward-int' }));
    expect(result.every((p) => p.status === 'admitted')).toBe(true);
    expect(result.some((p) => p.id === 'pat-005')).toBe(false);
  });

  it('does not report wardName/bed for a discharged patient', async () => {
    const result = await firstValueFrom(service.getPatients());
    const discharged = result.find((p) => p.id === 'pat-005');
    expect(discharged?.wardName).toBeUndefined();
    expect(discharged?.bed).toBeUndefined();
  });

  it('search matches last name case- and diacritic-insensitively', async () => {
    const result = await firstValueFrom(service.search('kowalski'));
    expect(result.some((p) => p.lastName === 'Kowalski')).toBe(true);
  });

  it('search matches PESEL', async () => {
    const result = await firstValueFrom(service.search('68031437976'));
    expect(result.some((p) => p.id === 'pat-001')).toBe(true);
  });

  it('getPatientById returns the full patient record', async () => {
    const patient = await firstValueFrom(service.getPatientById('pat-001'));
    expect(patient.firstName).toBe('Jan');
  });

  it('getPatientById errors for an unknown id', async () => {
    await expect(firstValueFrom(service.getPatientById('pat-999'))).rejects.toThrow();
  });

  it('findByPesel finds an existing patient', async () => {
    const found = await firstValueFrom(service.findByPesel('68031437976'));
    expect(found?.id).toBe('pat-001');
  });

  it('findByPesel returns null for an unused PESEL', async () => {
    const found = await firstValueFrom(service.findByPesel('99999999999'));
    expect(found).toBeNull();
  });

  it('createPatient persists a new patient with generated id and mrn', async () => {
    const before = await firstValueFrom(service.getPatients());
    const created = await firstValueFrom(service.createPatient(draft()));
    expect(created.id).toMatch(/^pat-\d{3}$/);
    expect(created.mrn).toMatch(/^HIS\/\d{4}\/\d{6}$/);

    const after = await firstValueFrom(service.getPatients());
    expect(after.length).toBe(before.length + 1);

    const fetched = await firstValueFrom(service.getPatientById(created.id));
    expect(fetched.firstName).toBe('Test');
  });

  it('updatePatient persists changes', async () => {
    const updated = await firstValueFrom(
      service.updatePatient('pat-003', { phone: '+48 500 000 000' }),
    );
    expect(updated.phone).toBe('+48 500 000 000');
    const fetched = await firstValueFrom(service.getPatientById('pat-003'));
    expect(fetched.phone).toBe('+48 500 000 000');
  });

  it('admitPatient sets status to admitted and stores the admission', async () => {
    const updated = await firstValueFrom(
      service.admitPatient('pat-006', {
        admissionType: 'planned',
        admittedAt: '2026-01-01T08:00:00.000Z',
        wardId: 'ward-int',
        attendingPhysicianId: 'stf-001',
        reason: 'Test',
      }),
    );
    expect(updated.status).toBe('admitted');
    expect(updated.currentAdmission?.wardId).toBe('ward-int');
  });

  it('dischargePatient sets status to discharged', async () => {
    const updated = await firstValueFrom(
      service.dischargePatient('pat-001', '2026-01-01T10:00:00.000Z'),
    );
    expect(updated.status).toBe('discharged');
  });
});
