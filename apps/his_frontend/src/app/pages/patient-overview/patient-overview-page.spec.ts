import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PatientOverviewPage } from './patient-overview-page';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';
import { PatientContextService } from '../../services/patient-context.service';
import { PatientService } from '../../services/patient.service';

describe('PatientOverviewPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        MessageService,
        ConfirmationService,
        { provide: MOCK_LATENCY_MS, useValue: 0 },
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
