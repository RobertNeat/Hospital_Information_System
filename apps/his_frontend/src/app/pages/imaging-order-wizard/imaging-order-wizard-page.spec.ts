import { labResultServiceStub } from '../../testing/lab-result-service.stub';
import { patientServiceStub } from '../../testing/patient-service.stub';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { staffServiceStub } from '../../testing/staff-service.stub';
import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';
import { ImagingOrderWizardPage } from './imaging-order-wizard-page';

describe('ImagingOrderWizardPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        patientServiceStub,
        ehrServiceStub,
        wardServiceStub,
        staffServiceStub,
        provideRouter([]),
        MessageService,
        labResultServiceStub,
        { provide: MOCK_LATENCY_MS, useValue: 0 },
      ],
    });
  });

  it('renders the page header', async () => {
    const fixture = TestBed.createComponent(ImagingOrderWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Nowe zlecenie badania obrazowego',
    );
  });

  it('reports no unsaved changes initially, and true once a step form becomes dirty', async () => {
    const fixture = TestBed.createComponent(ImagingOrderWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);

    fixture.componentInstance['step2Form'].controls.clinicalIndication.setValue(
      'Duszność wysiłkowa od tygodnia, podejrzenie zapalenia płuc.',
    );
    fixture.componentInstance['step2Form'].controls.clinicalIndication.markAsDirty();
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(true);
  });

  it('blocks advancing from step 1 when no modality/exam is selected', async () => {
    const fixture = TestBed.createComponent(ImagingOrderWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    let activated: number | null = null;
    fixture.componentInstance['advanceFromStep1']((v: number) => (activated = v));
    expect(activated).toBeNull();
  });

  it('does not require pregnancy screening for a male patient', async () => {
    const fixture = TestBed.createComponent(ImagingOrderWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    fixture.componentInstance['step1Form'].controls.modality.setValue('RTG');
    expect(fixture.componentInstance['needsPregnancyCheck']()).toBe(false);
  });
});
