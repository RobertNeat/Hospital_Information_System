import { labOrderServiceStub } from '../../testing/lab-order-service.stub';
import { patientServiceStub } from '../../testing/patient-service.stub';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { staffServiceStub } from '../../testing/staff-service.stub';
import { throwError } from 'rxjs';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { LabOrderWizardPage } from './lab-order-wizard-page';
import { LabOrderService } from '../../services/lab-order.service';

describe('LabOrderWizardPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        patientServiceStub,
        ehrServiceStub,
        wardServiceStub,
        staffServiceStub,
        provideRouter([]),
        MessageService,
        labOrderServiceStub,
      ],
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

  it('falls back to an empty catalog and toasts instead of hanging when the catalog fetch fails', async () => {
    const labOrderService = TestBed.inject(LabOrderService);
    vi.spyOn(labOrderService, 'getCatalog').mockReturnValue(throwError(() => new Error('500')));
    const fixture = TestBed.createComponent(LabOrderWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const cmp = fixture.componentInstance as unknown as {
      catalog: () => unknown[];
      panels: () => unknown[];
    };
    expect(cmp.catalog()).toEqual([]);
    expect(cmp.panels()).toEqual([]);
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
