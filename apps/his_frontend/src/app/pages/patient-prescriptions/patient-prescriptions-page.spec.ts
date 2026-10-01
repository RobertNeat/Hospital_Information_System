import { prescriptionServiceStub } from '../../testing/prescription-service.stub';
import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PatientPrescriptionsPage } from './patient-prescriptions-page';

describe('PatientPrescriptionsPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [prescriptionServiceStub, provideRouter([]), MessageService, ConfirmationService],
    });
  });

  it('renders the page header', async () => {
    const fixture = TestBed.createComponent(PatientPrescriptionsPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Leki i recepty');
  });

  it('lists issued prescriptions for the patient (pat-001 has rx-001 and rx-002)', async () => {
    const fixture = TestBed.createComponent(PatientPrescriptionsPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect(fixture.componentInstance['prescriptions']().length).toBeGreaterThanOrEqual(2);
  });

  it('derives active medications only from non-expired, issued prescriptions', async () => {
    const fixture = TestBed.createComponent(PatientPrescriptionsPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const active = fixture.componentInstance['activeMedications']();
    // rx-001 (issued, not expired) contributes Furosemid + Polpril; rx-002 is expired.
    expect(active.some((r) => r.drugName === 'Polpril')).toBe(true);
    expect(active.some((r) => r.drugName === 'Metformax')).toBe(false);
  });
});
