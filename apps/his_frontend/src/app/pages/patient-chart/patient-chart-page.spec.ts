import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { PatientChartPage } from './patient-chart-page';
import { EhrService } from '../../services/ehr.service';
import { TeamMessageService } from '../../services/team-message.service';
import { PatientContextService } from '../../services/patient-context.service';
import type { Patient } from '../../models';

const PATIENT: Patient = {
  id: 'pat-001',
  mrn: 'HIS/2026/000001',
  pesel: '90010112345',
  firstName: 'Jan',
  lastName: 'Kowalski',
  birthDate: '1990-01-01',
  gender: 'male',
  address: {
    street: 'Testowa',
    buildingNumber: '1',
    postalCode: '00-001',
    city: 'Warszawa',
    country: 'Polska',
  },
  insurance: { status: 'active', nfzBranch: '07', payer: 'NFZ' },
  status: 'admitted',
  flags: [],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('PatientChartPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: EhrService, useValue: { getAllergies: () => of([]) } },
        { provide: TeamMessageService, useValue: { getAlerts: () => of([]) } },
      ],
    });
  });

  it('renders the sticky bar and chart nav once a patient is in context', async () => {
    const fixture = TestBed.createComponent(PatientChartPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    TestBed.inject(PatientContextService).setPatient(PATIENT);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.his-patient-sticky')).toBeTruthy();
    expect(el.querySelector('app-patient-context-bar')).toBeTruthy();
    expect(el.querySelector('app-patient-chart-nav')).toBeTruthy();
  });

  it('does not render the bar when no patient is in context', async () => {
    const fixture = TestBed.createComponent(PatientChartPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.his-patient-sticky')).toBeFalsy();
  });
});
