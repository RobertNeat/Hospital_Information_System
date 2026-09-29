import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { DashboardPage } from './dashboard-page';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';
import { PatientContextService } from '../../services/patient-context.service';
import type { PatientSummary } from '../../models';

const SUMMARY: PatientSummary = {
  id: 'pat-001',
  mrn: 'HIS/2026/000001',
  pesel: '90010112345',
  firstName: 'Jan',
  lastName: 'Kowalski',
  birthDate: '1990-01-01',
  gender: 'male',
  status: 'admitted',
  flags: [],
};

describe('DashboardPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: MOCK_LATENCY_MS, useValue: 0 }],
    });
  });

  it('renders the greeting with the current user title and last name', async () => {
    const fixture = TestBed.createComponent(DashboardPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Dzień dobry');
    expect(el.textContent).toContain('Nowak');
  });

  it('renders the six stat cards', async () => {
    const fixture = TestBed.createComponent(DashboardPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Pacjenci na oddziale');
    expect(el.textContent).toContain('Nowe wyniki');
    expect(el.textContent).toContain('Alerty krytyczne');
    expect(el.textContent).toContain('Moje zadania');
    expect(el.textContent).toContain('Oczekujące zlecenia');
    expect(el.textContent).toContain('Odchylenia parametrów');
  });

  it('navigates to the patient overview when a patient is picked from the search', async () => {
    const fixture = TestBed.createComponent(DashboardPage);
    await fixture.whenStable();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    (
      fixture.componentInstance as unknown as { goToPatient: (p: PatientSummary) => void }
    ).goToPatient(SUMMARY);

    expect(navigateSpy).toHaveBeenCalledWith('/patients/pat-001/overview');
  });

  it('navigates quick actions to their routes', async () => {
    const fixture = TestBed.createComponent(DashboardPage);
    await fixture.whenStable();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    (fixture.componentInstance as unknown as { onQuickAction: (id: string) => void }).onQuickAction(
      'register-patient',
    );
    expect(navigateSpy).toHaveBeenCalledWith('/patients/register');

    (fixture.componentInstance as unknown as { onQuickAction: (id: string) => void }).onQuickAction(
      'lab-order',
    );
    expect(navigateSpy).toHaveBeenCalledWith('/orders/lab/new');
  });

  it('falls back to admitted patients when there are no recently viewed patients', async () => {
    const fixture = TestBed.createComponent(DashboardPage);
    await fixture.whenStable();
    const ctx = TestBed.inject(PatientContextService);
    expect(ctx.recentPatients().length).toBe(0);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Ostatnio przeglądani pacjenci');
  });
});
