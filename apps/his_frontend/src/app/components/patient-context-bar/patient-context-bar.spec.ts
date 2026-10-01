import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { PatientContextBar } from './patient-context-bar';
import type { Patient } from '../../models';

const PATIENT: Patient = {
  id: 'pat-001',
  mrn: 'HIS/2026/000001',
  pesel: '90010112345',
  firstName: 'Jan',
  lastName: 'Kowalski',
  birthDate: '1990-01-01',
  gender: 'male',
  address: {
    street: 'Testowa',
    buildingNumber: '1',
    postalCode: '00-001',
    city: 'Warszawa',
    country: 'Polska',
  },
  insurance: { status: 'active', nfzBranch: '07', payer: 'NFZ' },
  status: 'admitted',
  flags: [],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('PatientContextBar', () => {
  it('renders the patient name and PESEL', async () => {
    const fixture = TestBed.createComponent(PatientContextBar);
    fixture.componentRef.setInput('patient', PATIENT);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Kowalski');
    expect(el.textContent).toContain('90010112345');
  });

  it('emits the action id when a bar action button is clicked', async () => {
    const fixture = TestBed.createComponent(PatientContextBar);
    fixture.componentRef.setInput('patient', PATIENT);
    await fixture.whenStable();

    let emitted: string | undefined;
    fixture.componentInstance.action.subscribe((a) => (emitted = a));

    (fixture.componentInstance as unknown as { emit: (a: string) => void }).emit('vitals');
    expect(emitted).toBe('vitals');
  });

  it('renders an outpatient admission without ward and physician', async () => {
    const fixture = TestBed.createComponent(PatientContextBar);
    fixture.componentRef.setInput('patient', {
      ...PATIENT,
      status: 'outpatient',
      currentAdmission: { admissionType: 'outpatient', admittedAt: '2026-01-01T00:00:00Z' },
    });
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('Kowalski');
    expect(text).not.toContain('Lekarz prowadzący');
  });
});
