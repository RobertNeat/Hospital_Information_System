import { patientServiceStub } from '../../testing/patient-service.stub';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { staffServiceStub } from '../../testing/staff-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { throwError } from 'rxjs';
import { afterEach, describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PatientService } from '../../services/patient.service';
import { PatientRegistrationPage } from './patient-registration-page';

describe('PatientRegistrationPage', () => {
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
        staffServiceStub,
        provideRouter([]),
        MessageService,
        ConfirmationService,
        wardServiceStub,
      ],
    });
  });

  it('renders the create-mode title by default', async () => {
    const fixture = TestBed.createComponent(PatientRegistrationPage);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Rejestracja pacjenta');
  });

  it('renders the edit-mode title when mode is edit', async () => {
    const fixture = TestBed.createComponent(PatientRegistrationPage);
    fixture.componentRef.setInput('mode', 'edit');
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Edycja danych pacjenta');
  });

  it('stops the loading spinner and navigates back instead of hanging when the fetch fails', async () => {
    const patientService = TestBed.inject(PatientService);
    vi.spyOn(patientService, 'getPatientById').mockReturnValue(throwError(() => new Error('404')));
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate');
    const fixture = TestBed.createComponent(PatientRegistrationPage);
    fixture.componentRef.setInput('mode', 'edit');
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect(fixture.componentInstance['loadingPatient']()).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith(['/patients']);
  });

  it('reports no unsaved changes before any step is touched', async () => {
    const fixture = TestBed.createComponent(PatientRegistrationPage);
    await fixture.whenStable();
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });

  it('reports unsaved changes once a step form becomes dirty', async () => {
    const fixture = TestBed.createComponent(PatientRegistrationPage);
    await fixture.whenStable();
    const instance = fixture.componentInstance as unknown as {
      step2: {
        controls: { firstName: { setValue: (v: string) => void; markAsDirty: () => void } };
      };
    };
    // setValue() alone does not mark a control dirty -- only real user input (or an
    // explicit markAsDirty()) does, matching what the ControlValueAccessor does on blur/input.
    instance.step2.controls.firstName.setValue('Jan');
    instance.step2.controls.firstName.markAsDirty();
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(true);
  });

  it('does not report unsaved changes after successful submit', async () => {
    const fixture = TestBed.createComponent(PatientRegistrationPage);
    await fixture.whenStable();
    const instance = fixture.componentInstance as unknown as {
      step2: {
        controls: { firstName: { setValue: (v: string) => void; markAsDirty: () => void } };
      };
      submitted: { set: (v: boolean) => void };
    };
    instance.step2.controls.firstName.setValue('Jan');
    instance.step2.controls.firstName.markAsDirty();
    instance.submitted.set(true);
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });

  it('flags a duplicate PESEL and blocks advancing past step 1', async () => {
    const fixture = TestBed.createComponent(PatientRegistrationPage);
    await fixture.whenStable();
    const instance = fixture.componentInstance as unknown as {
      step1: { controls: { pesel: { setValue: (v: string) => void } } };
      duplicatePatient: () => { id: string; label: string } | null;
    };
    // pat-001's PESEL from the mock data.
    instance.step1.controls.pesel.setValue('68031437976');
    await fixture.whenStable();
    expect(instance.duplicatePatient()).not.toBeNull();
  });

  it('prefills the edit-mode forms from the loaded patient and stays pristine', async () => {
    const fixture = TestBed.createComponent(PatientRegistrationPage);
    fixture.componentRef.setInput('mode', 'edit');
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    const instance = fixture.componentInstance as unknown as {
      step2: { controls: { lastName: { value: string } } };
      hasUnsavedChanges: () => boolean;
    };
    expect(instance.step2.controls.lastName.value).toBe('Kowalski');
    expect(instance.hasUnsavedChanges()).toBe(false);
  });

  it('reflects live form edits in the summary sections', async () => {
    const fixture = TestBed.createComponent(PatientRegistrationPage);
    await fixture.whenStable();
    const instance = fixture.componentInstance as unknown as {
      step2: { controls: { firstName: { setValue: (v: string) => void } } };
      summarySections: () => { items: { value: string | null }[] }[];
    };
    instance.step2.controls.firstName.setValue('Janusz');
    const sections = instance.summarySections();
    const values = sections.flatMap((s) => s.items.map((i) => i.value));
    expect(values.some((v) => v?.includes('Janusz'))).toBe(true);
  });

  it('reflects the outpatient admission-type toggle live', async () => {
    const fixture = TestBed.createComponent(PatientRegistrationPage);
    await fixture.whenStable();
    const instance = fixture.componentInstance as unknown as {
      step4: { controls: { admissionType: { setValue: (v: string) => void } } };
      isAmbulatoryOnly: () => boolean;
    };
    expect(instance.isAmbulatoryOnly()).toBe(false);
    instance.step4.controls.admissionType.setValue('outpatient');
    expect(instance.isAmbulatoryOnly()).toBe(true);
  });

  it('admits an outpatient with type and time only (patient becomes outpatient)', async () => {
    const fixture = TestBed.createComponent(PatientRegistrationPage);
    await fixture.whenStable();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const admit = vi.spyOn(TestBed.inject(PatientService), 'admitPatient');
    const instance = fixture.componentInstance as unknown as {
      step4: { controls: { admissionType: { setValue: (v: string) => void } } };
      submit: () => void;
    };
    instance.step4.controls.admissionType.setValue('outpatient');
    instance.submit();
    expect(admit).toHaveBeenCalledTimes(1);
    const body = admit.mock.calls[0][1];
    expect(Object.keys(body).sort()).toEqual(['admissionType', 'admittedAt']);
    expect(body.admissionType).toBe('outpatient');
    let status: string | undefined;
    admit.mock.results[0].value.subscribe((p: { status: string }) => (status = p.status));
    expect(status).toBe('outpatient');
  });
});
