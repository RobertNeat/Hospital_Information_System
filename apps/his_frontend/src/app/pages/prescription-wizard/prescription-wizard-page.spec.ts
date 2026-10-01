import { drugServiceStub } from '../../testing/drug-service.stub';
import { prescriptionServiceStub } from '../../testing/prescription-service.stub';
import { patientServiceStub } from '../../testing/patient-service.stub';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { staffServiceStub } from '../../testing/staff-service.stub';
import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PrescriptionWizardPage } from './prescription-wizard-page';

describe('PrescriptionWizardPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        patientServiceStub,
        ehrServiceStub,
        wardServiceStub,
        staffServiceStub,
        drugServiceStub,
        prescriptionServiceStub,
        provideRouter([]),
        MessageService,
        ConfirmationService,
      ],
    });
  });

  it('renders the page header', async () => {
    const fixture = TestBed.createComponent(PrescriptionWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nowa recepta');
  });

  it('reports no unsaved changes initially', () => {
    const fixture = TestBed.createComponent(PrescriptionWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });

  it('reports unsaved changes once a drug is selected', async () => {
    const fixture = TestBed.createComponent(PrescriptionWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    fixture.componentInstance['selectedDrug'].set({
      id: 'drg-001',
      name: 'Polpril',
      activeSubstance: 'Ramipril',
      atcCode: 'C09AA05',
      form: 'tablet',
      strength: '5 mg',
      packageSize: 28,
      packageUnit: 'tabl.',
      routes: ['oral'],
      defaultDoseUnit: 'mg',
      rxOnly: true,
      reimbursementOptions: ['30%'],
    });
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(true);
  });

  it('flags a life-threatening penicillin allergy as an allergy danger for pat-001 + Augmentin', async () => {
    const fixture = TestBed.createComponent(PrescriptionWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    fixture.componentInstance['onDrugSelected']({
      id: 'drg-011',
      name: 'Augmentin',
      activeSubstance: 'Amoksycylina + kwas klawulanowy',
      atcCode: 'J01CR02',
      form: 'tablet',
      strength: '875/125 mg',
      packageSize: 14,
      packageUnit: 'tabl.',
      routes: ['oral'],
      defaultDoseUnit: 'mg',
      rxOnly: true,
      reimbursementOptions: ['100%', 'none'],
    });
    await fixture.whenStable();
    expect(fixture.componentInstance['hasAllergyDanger']()).toBe(true);
  });
});
