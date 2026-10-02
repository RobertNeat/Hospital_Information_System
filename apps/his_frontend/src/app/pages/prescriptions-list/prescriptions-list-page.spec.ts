import { patientServiceStub } from '../../testing/patient-service.stub';
import { prescriptionServiceStub } from '../../testing/prescription-service.stub';
import { staffServiceStub } from '../../testing/staff-service.stub';
import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PrescriptionsListPage } from './prescriptions-list-page';

describe('PrescriptionsListPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [staffServiceStub, prescriptionServiceStub, patientServiceStub, provideRouter([])],
    });
  });

  it('renders the page header', async () => {
    const fixture = TestBed.createComponent(PrescriptionsListPage);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Recepty');
  });

  it('loads prescriptions issued by the current user (stf-001 has several)', async () => {
    const fixture = TestBed.createComponent(PrescriptionsListPage);
    await fixture.whenStable();
    expect(fixture.componentInstance['prescriptions']().length).toBeGreaterThan(0);
    expect(
      fixture.componentInstance['prescriptions']().every((p) => p.prescriberId === 'stf-001'),
    ).toBe(true);
  });

  it('resolves the patient column to a name instead of the raw UUID', async () => {
    const fixture = TestBed.createComponent(PrescriptionsListPage);
    await fixture.whenStable();
    const row = fixture.componentInstance['filteredRows']().find(
      (r: { patientId: string }) => r.patientId === 'pat-001',
    );
    expect(row?.patientName).toBe('Kowalski Jan');
  });

  it('falls back to the UUID when the patient cannot be resolved', async () => {
    const fixture = TestBed.createComponent(PrescriptionsListPage);
    await fixture.whenStable();
    fixture.componentInstance['prescriptions'].set([
      ...fixture.componentInstance['prescriptions'](),
      {
        id: 'test-rx-unknown',
        patientId: 'unknown-patient',
        prescriberId: 'stf-001',
        issuedAt: '2026-01-01T00:00:00Z',
        validFrom: '2026-01-01',
        validUntil: '2026-02-01',
        kind: 'hospital_order',
        items: [],
        status: 'issued',
        accessCode: '0000',
        version: 0,
      },
    ]);
    const row = fixture.componentInstance['filteredRows']().find(
      (r: { patientId: string }) => r.patientId === 'unknown-patient',
    );
    expect(row?.patientName).toBe('unknown-patient');
  });

  it('filters by status', async () => {
    const fixture = TestBed.createComponent(PrescriptionsListPage);
    await fixture.whenStable();
    const totalBefore = fixture.componentInstance['filteredRows']().length;
    fixture.componentInstance['statusFilter'].set('cancelled');
    const filtered = fixture.componentInstance['filteredRows']();
    expect(filtered.length).toBeLessThanOrEqual(totalBefore);
    expect(filtered.every((r) => r.status === 'cancelled')).toBe(true);
  });
});
