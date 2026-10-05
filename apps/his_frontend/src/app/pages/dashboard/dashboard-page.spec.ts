import { labResultServiceStub } from '../../testing/lab-result-service.stub';
import { patientServiceStub } from '../../testing/patient-service.stub';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import {
  DASHBOARD_STATS_FIXTURE,
  dashboardServiceStub,
} from '../../testing/dashboard-service.stub';
import { teamMessageServiceStub } from '../../testing/team-message-service.stub';
import { staffServiceStub } from '../../testing/staff-service.stub';
import { authServiceStub } from '../../testing/auth-service.stub';
import { afterEach, describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { DashboardPage } from './dashboard-page';
import { PatientContextService } from '../../services/patient-context.service';
import { AuthService } from '../../services/auth.service';
import type { ClinicalAlert, PatientSummary } from '../../models';

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
  // Storage must not leak into other specs (auth.service.spec asserts it is empty).
  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        patientServiceStub,
        ehrServiceStub,
        wardServiceStub,
        staffServiceStub,
        provideRouter([]),
        labResultServiceStub,
        dashboardServiceStub,
        teamMessageServiceStub,
        authServiceStub,
      ],
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

  it('shows the counters returned by the backend stats endpoint', async () => {
    const fixture = TestBed.createComponent(DashboardPage);
    await fixture.whenStable();
    expect(fixture.componentInstance['stats']()).toEqual(DASHBOARD_STATS_FIXTURE);
  });

  it('links a critical alert through its target and falls back to the patient overview', async () => {
    const fixture = TestBed.createComponent(DashboardPage);
    await fixture.whenStable();
    const page = fixture.componentInstance as unknown as {
      alertLink: (a: Partial<ClinicalAlert>) => { commands: string[] } | null;
    };
    expect(
      page.alertLink({ target: { kind: 'lab_result', id: 'lres-1', patientId: 'pat-001' } })
        ?.commands,
    ).toEqual(['/patients', 'pat-001', 'results', 'lab', 'lres-1']);
    expect(page.alertLink({ patientId: 'pat-002' })?.commands).toEqual([
      '/patients',
      'pat-002',
      'overview',
    ]);
    expect(page.alertLink({})).toBeNull();
  });

  it('hides quick actions and stat-card links the role has no permission for', async () => {
    TestBed.overrideProvider(AuthService, {
      // Rejestrator: no vitals/orders/results/alerts/tasks; but patient:write is registrar's.
      useValue: { hasPermission: (p: string) => p === 'patient:write' },
    });
    const fixture = TestBed.createComponent(DashboardPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Rejestracja pacjenta');
    // Scoped to the stat-card grid: "Alerty krytyczne" also titles the (always-visible) section below.
    const statGrid = el.querySelector('#dashboard-grid-3');
    const statText = statGrid?.textContent ?? '';
    expect(statText).not.toContain('Odchylenia parametrów');
    expect(statText).not.toContain('Oczekujące zlecenia');
    expect(statText).not.toContain('Nowe wyniki');
    expect(statText).not.toContain('Alerty krytyczne');
    expect(statText).not.toContain('Moje zadania');
    // `/patients` has no route guard, so the admitted-patients tile always stays.
    expect(statText).toContain('Pacjenci na oddziale');
  });

  it('hides the register-patient quick action without `patient:write`', async () => {
    TestBed.overrideProvider(AuthService, {
      useValue: { hasPermission: () => false },
    });
    const fixture = TestBed.createComponent(DashboardPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).not.toContain('Rejestracja pacjenta');
  });

  it('names an inbox result without `patient` by resolving its patientId', async () => {
    const fixture = TestBed.createComponent(DashboardPage);
    await fixture.whenStable();
    const page = fixture.componentInstance as unknown as {
      resultPatientName: (r: unknown) => string;
      resolveMissingPatients: (r: unknown[]) => void;
    };
    const row = { patientId: 'pat-001' };
    expect(page.resultPatientName({ ...row, patient: SUMMARY })).toBe('Kowalski Jan');
    expect(page.resultPatientName(row)).toBe('');

    page.resolveMissingPatients([row]);
    await fixture.whenStable();
    expect(page.resultPatientName(row)).not.toBe('');
  });
});
