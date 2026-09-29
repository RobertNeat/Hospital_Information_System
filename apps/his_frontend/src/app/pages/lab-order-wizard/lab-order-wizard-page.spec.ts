import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';
import { LabOrderWizardPage } from './lab-order-wizard-page';

describe('LabOrderWizardPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), MessageService, { provide: MOCK_LATENCY_MS, useValue: 0 }],
    });
  });

  it('renders the page header', async () => {
    const fixture = TestBed.createComponent(LabOrderWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Nowe zlecenie laboratoryjne',
    );
  });

  it('reports no unsaved changes initially, and true once a step form becomes dirty', async () => {
    const fixture = TestBed.createComponent(LabOrderWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);

    fixture.componentInstance['step3Form'].controls.clinicalInfo.setValue('Kontrola po leczeniu');
    fixture.componentInstance['step3Form'].controls.clinicalInfo.markAsDirty();
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(true);
  });

  it('blocks advancing from step 1 when no test is selected', async () => {
    const fixture = TestBed.createComponent(LabOrderWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    let activated: number | null = null;
    fixture.componentInstance['advanceFromStep1']((v: number) => (activated = v));
    expect(activated).toBeNull();
    expect(fixture.componentInstance['step1Form'].controls.selectedCodes.touched).toBe(true);
  });

  it('advances from step 1 once at least one test is selected', async () => {
    const fixture = TestBed.createComponent(LabOrderWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    fixture.componentInstance['onSelectedCodesChange'](['MORF']);
    let activated: number | null = null;
    fixture.componentInstance['advanceFromStep1']((v: number) => (activated = v));
    expect(activated).toBe(2);
  });
});
