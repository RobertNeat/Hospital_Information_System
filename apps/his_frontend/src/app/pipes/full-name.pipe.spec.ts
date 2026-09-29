import { describe, expect, it } from 'vitest';
import { FullNamePipe } from './full-name.pipe';
import type { PatientSummary, StaffMember } from '../models';

const patient: PatientSummary = {
  id: 'pat-001',
  mrn: 'HIS/2026/000001',
  pesel: '68031437976',
  firstName: 'Jan',
  lastName: 'Kowalski',
  birthDate: '1968-03-14',
  gender: 'male',
  status: 'admitted',
  flags: [],
};

const staff: StaffMember = {
  id: 'stf-001',
  title: 'lek.',
  firstName: 'Anna',
  lastName: 'Nowak',
  role: 'doctor',
  wardId: 'ward-int',
  online: true,
};

describe('FullNamePipe', () => {
  const pipe = new FullNamePipe();

  it('formats a patient as "Nazwisko Imię"', () => {
    expect(pipe.transform(patient)).toBe('Kowalski Jan');
  });

  it('formats staff with title prefixed', () => {
    expect(pipe.transform(staff)).toBe('lek. Nowak Anna');
  });

  it('returns an empty string for null/undefined', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});
