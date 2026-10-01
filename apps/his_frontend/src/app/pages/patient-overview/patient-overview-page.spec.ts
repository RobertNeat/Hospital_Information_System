import { patientServiceStub } from '../../testing/patient-service.stub';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { vitalsServiceStub } from '../../testing/vitals-service.stub';
import { staffServiceStub } from '../../testing/staff-service.stub';
import { afterEach, describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PatientOverviewPage } from './patient-overview-page';
import { PatientContextService } from '../../services/patient-context.service';
import { PatientService } from '../../services/patient.service';

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
});
