import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { VitalsBoardPage } from './vitals-board-page';

describe('VitalsBoardPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: MOCK_LATENCY_MS, useValue: 0 }, wardServiceStub],
    });
  });

  it('renders the page header and stat cards from the ward overview', async () => {
    const fixture = TestBed.createComponent(VitalsBoardPage);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Monitoring parametrów');
    expect(fixture.componentInstance['patientsMonitored']()).toBeGreaterThan(0);
  });

  it('counts patients with no measurement in the last 8 hours', async () => {
    const fixture = TestBed.createComponent(VitalsBoardPage);
    await fixture.whenStable();

    const rows = fixture.componentInstance['rows']();
    const expected = rows.filter(
      (r) => r.lastMeasuredAgoMin === null || r.lastMeasuredAgoMin > 8 * 60,
    ).length;
    expect(fixture.componentInstance['noRecentMeasurementCount']()).toBe(expected);
  });

  it('navigates to the patient vitals page on row selection', async () => {
    const fixture = TestBed.createComponent(VitalsBoardPage);
    await fixture.whenStable();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate');

    const rows = fixture.componentInstance['rows']();
    fixture.componentInstance['onRowSelect'](rows[0]);

    expect(navigateSpy).toHaveBeenCalledWith(['/patients', rows[0].patientId, 'vitals']);
  });

  it('updates the ward query param when the ward select changes', async () => {
    const fixture = TestBed.createComponent(VitalsBoardPage);
    await fixture.whenStable();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate');

    fixture.componentInstance['onWardChange']('ward-int');

    expect(navigateSpy).toHaveBeenCalledWith([], {
      queryParams: { ward: 'ward-int' },
      queryParamsHandling: 'merge',
    });
  });
});
