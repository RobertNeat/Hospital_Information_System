import { wardServiceStub } from '../../testing/ward-service.stub';
import { afterEach, describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { PATIENTS } from '../../mock-data/patients.mock';
import { PatientPickerPage } from './patient-picker-page';
import { PatientService } from '../../services/patient.service';
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

describe('PatientPickerPage', () => {
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
        wardServiceStub,
        provideRouter([]),
        { provide: PatientService, useValue: { search: () => of([SUMMARY]) } },
      ],
    });
  });

  it('renders the page header', async () => {
    const fixture = TestBed.createComponent(PatientPickerPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Wybór pacjenta');
  });

  it('navigates to /patients/:id/<next> when a patient is chosen, preserving slashes in next', async () => {
    const fixture = TestBed.createComponent(PatientPickerPage);
    fixture.componentRef.setInput('next', 'orders/lab/new');
    await fixture.whenStable();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    (fixture.componentInstance as unknown as { goTo: (p: PatientSummary) => void }).goTo(SUMMARY);

    expect(navigateSpy).toHaveBeenCalledWith('/patients/pat-001/orders/lab/new');
  });

  it('shows recent patients from PatientContextService', async () => {
    const fixture = TestBed.createComponent(PatientPickerPage);
    const ctx = TestBed.inject(PatientContextService);
    ctx.setPatient(PATIENTS.find((p) => p.lastName === 'Kowalski') ?? PATIENTS[0]);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Kowalski');
  });
});
