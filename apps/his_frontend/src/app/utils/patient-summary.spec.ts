import { describe, expect, it } from 'vitest';
import type { Patient, Ward } from '../models';
import { toPatientSummary } from './patient-summary';

const wards = [{ id: 'w1', name: 'Kardiologia' } as Ward];

function makePatient(over: Partial<Patient> = {}): Patient {
  return {
    id: 'p1',
    mrn: 'HIS/2026/000001',
    pesel: null,
    firstName: 'Jan',
    lastName: 'Kowalski',
    birthDate: '1980-01-01',
    gender: 'male',
    address: {
      street: 'A',
      buildingNumber: '1',
      postalCode: '00-001',
      city: 'Warszawa',
      country: 'PL',
    },
    insurance: { status: 'active', nfzBranch: '01', payer: 'NFZ' },
    status: 'admitted',
    flags: ['dnr'],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  };
}

const admission: NonNullable<Patient['currentAdmission']> = {
  admissionType: 'planned',
  admittedAt: '2026-01-01T00:00:00Z',
  wardId: 'w1',
  bed: '12A',
  attendingPhysicianId: 's1',
  reason: 'x',
};

describe('toPatientSummary', () => {
  it('includes wardName and bed for admitted patients', () => {
    const s = toPatientSummary(makePatient({ currentAdmission: admission }), wards);
    expect(s).toMatchObject({ id: 'p1', flags: ['dnr'], wardName: 'Kardiologia', bed: '12A' });
  });

  it('omits wardName and bed for discharged patients with stale admission', () => {
    const s = toPatientSummary(
      makePatient({ status: 'discharged', currentAdmission: admission }),
      wards,
    );
    expect(s.wardName).toBeUndefined();
    expect(s.bed).toBeUndefined();
  });

  it('handles missing currentAdmission', () => {
    const s = toPatientSummary(makePatient(), wards);
    expect(s.wardName).toBeUndefined();
    expect(s.bed).toBeUndefined();
  });

  it('handles an outpatient admission without ward or physician', () => {
    const s = toPatientSummary(
      makePatient({
        status: 'outpatient',
        currentAdmission: { admissionType: 'outpatient', admittedAt: '2026-01-01T00:00:00Z' },
      }),
      wards,
    );
    expect(s.wardName).toBeUndefined();
    expect(s.bed).toBeUndefined();
  });
});
