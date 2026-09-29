import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import type { Patient, Prescription, StaffMember } from '../../models';
import { EPrescriptionDialog } from './e-prescription-dialog';

const PRESCRIPTION: Prescription = {
  id: 'rx-999',
  patientId: 'pat-001',
  prescriberId: 'stf-001',
  issuedAt: '2026-09-29T10:00:00.000Z',
  validFrom: '2026-09-29',
  validUntil: '2026-10-29',
  kind: 'e_prescription',
  items: [],
  status: 'issued',
  accessCode: '1234',
  eRxKey: 'A'.repeat(44),
};

const PATIENT = {
  id: 'pat-001',
  firstName: 'Jan',
  lastName: 'Kowalski',
  pesel: '90010112345',
} as Patient;

const PRESCRIBER = {
  id: 'stf-001',
  title: 'lek.',
  firstName: 'Anna',
  lastName: 'Nowak',
} as StaffMember;

describe('EPrescriptionDialog', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('shows the access code and eRx key', async () => {
    const fixture = TestBed.createComponent(EPrescriptionDialog);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('prescription', PRESCRIPTION);
    fixture.componentRef.setInput('patient', PATIENT);
    fixture.componentRef.setInput('prescriber', PRESCRIBER);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('1234');
    expect(text).toContain('A'.repeat(44));
  });

  it('emits closed()', async () => {
    const fixture = TestBed.createComponent(EPrescriptionDialog);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('prescription', PRESCRIPTION);
    fixture.componentRef.setInput('patient', PATIENT);
    fixture.componentRef.setInput('prescriber', PRESCRIBER);
    await fixture.whenStable();
    let closed = false;
    fixture.componentInstance.closed.subscribe(() => (closed = true));
    fixture.componentInstance['close']();
    expect(closed).toBe(true);
  });
});
