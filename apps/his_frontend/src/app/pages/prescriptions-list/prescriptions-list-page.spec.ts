import { prescriptionServiceStub } from '../../testing/prescription-service.stub';
import { staffServiceStub } from '../../testing/staff-service.stub';
import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';
import { PrescriptionsListPage } from './prescriptions-list-page';

describe('PrescriptionsListPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        staffServiceStub,
        prescriptionServiceStub,
        provideRouter([]),
        { provide: MOCK_LATENCY_MS, useValue: 0 },
      ],
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
