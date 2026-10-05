import { patientServiceStub } from '../../testing/patient-service.stub';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { vitalsServiceStub } from '../../testing/vitals-service.stub';
import { staffServiceStub } from '../../testing/staff-service.stub';
import { throwError } from 'rxjs';
import { afterEach, describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PatientOverviewPage } from './patient-overview-page';
import { PatientContextService } from '../../services/patient-context.service';
import { PatientService } from '../../services/patient.service';
import { AuthService } from '../../services/auth.service';
import { EhrService } from '../../services/ehr.service';

describe('PatientOverviewPage', () => {
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
        MessageService,
        ConfirmationService,
        vitalsServiceStub,
      ],
    });
  });

  it('renders the page header', async () => {
    const fixture = TestBed.createComponent(PatientOverviewPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Dane pacjenta');
  });

  it('renders basic patient info once the context has a patient', async () => {
    const fixture = TestBed.createComponent(PatientOverviewPage);
    fixture.componentRef.setInput('patientId', 'pat-001');

    const patientService = TestBed.inject(PatientService);
    const ctx = TestBed.inject(PatientContextService);
    const patient = await new Promise((resolve) =>
      patientService.getPatientById('pat-001').subscribe(resolve),
    );
    ctx.setPatient(patient as never);

    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Dane podstawowe');
    expect(text).toContain('Dane kontaktowe');
    expect(text).toContain('Ubezpieczenie');
  });

  it('hides the vitals quick action, section and call for a role without `vitals:read`', async () => {
    TestBed.overrideProvider(AuthService, { useValue: { hasPermission: () => false } });
    const fixture = TestBed.createComponent(PatientOverviewPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    const patientService = TestBed.inject(PatientService);
    const ctx = TestBed.inject(PatientContextService);
    const patient = await new Promise((resolve) =>
      patientService.getPatientById('pat-001').subscribe(resolve),
    );
    ctx.setPatient(patient as never);
    await fixture.whenStable();
    const page = fixture.componentInstance as unknown as {
      vitalsLoaded: () => boolean;
      latestVitals: () => unknown;
    };
    // Gated off, not pending forever: `vitalsLoaded` still resolves so the section doesn't spin.
    expect(page.vitalsLoaded()).toBe(true);
    expect(page.latestVitals()).toBeUndefined();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Parametry życiowe');
    // Gated off entirely: never a false "Brak pomiarów" (no measurements) for unverified data.
    expect(text).not.toContain('Brak pomiarów');
  });

  it('for a registrar-like role (no ehr/vitals/results/orders/prescriptions read): avoids the allergies call, hides the allergies/vitals sections and all 5 quick actions', async () => {
    TestBed.overrideProvider(AuthService, {
      useValue: { hasPermission: (p: string) => p === 'patient:write' },
    });
    const fixture = TestBed.createComponent(PatientOverviewPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    const patientService = TestBed.inject(PatientService);
    const ctx = TestBed.inject(PatientContextService);
    const patient = await new Promise((resolve) =>
      patientService.getPatientById('pat-001').subscribe(resolve),
    );
    ctx.setPatient(patient as never);
    await fixture.whenStable();
    const page = fixture.componentInstance as unknown as {
      allergies: () => unknown[];
      allergiesUnavailable: () => boolean;
    };
    // Gated off (not a fetch failure): `allergiesUnavailable` stays false, the call never fires,
    // and the section itself is hidden rather than showing a false "brak alergii" or error.
    expect(page.allergiesUnavailable()).toBe(false);
    expect(page.allergies()).toEqual([]);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Alergie');
    expect(text).not.toContain('Brak odnotowanych alergii');
    expect(text).not.toContain('Historia choroby');
    expect(text).not.toContain('Wyniki badań');
    expect(text).not.toContain('Parametry życiowe');
    expect(text).not.toContain('Zlecenia');
    expect(text).not.toContain('Leki i recepty');
  });

  it('loads allergies and shows a genuine "no allergies" for a role with `ehr:read-limited`', async () => {
    TestBed.overrideProvider(AuthService, {
      useValue: { hasPermission: (p: string) => p === 'ehr:read-limited' },
    });
    const fixture = TestBed.createComponent(PatientOverviewPage);
    fixture.componentRef.setInput('patientId', 'pat-002');
    const patientService = TestBed.inject(PatientService);
    const ctx = TestBed.inject(PatientContextService);
    const patient = await new Promise((resolve) =>
      patientService.getPatientById('pat-002').subscribe(resolve),
    );
    ctx.setPatient(patient as never);
    await fixture.whenStable();
    const page = fixture.componentInstance as unknown as {
      allergiesUnavailable: () => boolean;
    };
    expect(page.allergiesUnavailable()).toBe(false);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Alergie');
  });

  it('shows "unable to load" (not a false "no allergies") when the allergies call fails for a permitted role', async () => {
    TestBed.overrideProvider(AuthService, {
      useValue: { hasPermission: (p: string) => p === 'ehr:read-limited' },
    });
    const ehrService = TestBed.inject(EhrService);
    vi.spyOn(ehrService, 'getAllergies').mockReturnValue(throwError(() => new Error('500')));
    const fixture = TestBed.createComponent(PatientOverviewPage);
    fixture.componentRef.setInput('patientId', 'pat-002');
    const patientService = TestBed.inject(PatientService);
    const ctx = TestBed.inject(PatientContextService);
    const patient = await new Promise((resolve) =>
      patientService.getPatientById('pat-002').subscribe(resolve),
    );
    ctx.setPatient(patient as never);
    await fixture.whenStable();
    const page = fixture.componentInstance as unknown as { allergiesUnavailable: () => boolean };
    expect(page.allergiesUnavailable()).toBe(true);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Nie udało się wczytać alergii');
    expect(text).not.toContain('Brak odnotowanych alergii');
  });
});
