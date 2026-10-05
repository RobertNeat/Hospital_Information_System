import { imagingOrderServiceStub } from '../../testing/imaging-order-service.stub';
import { labResultServiceStub } from '../../testing/lab-result-service.stub';
import { patientServiceStub } from '../../testing/patient-service.stub';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { staffServiceStub } from '../../testing/staff-service.stub';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ImagingOrderService } from '../../services/imaging-order.service';
import { EhrService } from '../../services/ehr.service';
import { LabResultService } from '../../services/lab-result.service';
import type { ImagingOrder, ScheduleSlot } from '../../models';
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
        imagingOrderServiceStub,
      ],
    });
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  it('falls back to an empty catalog and toasts instead of hanging when the catalog fetch fails', async () => {
    const imagingOrderService = TestBed.inject(ImagingOrderService);
    vi.spyOn(imagingOrderService, 'getCatalog').mockReturnValue(throwError(() => new Error('500')));
    const fixture = TestBed.createComponent(ImagingOrderWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const fullCatalog = (
      fixture.componentInstance as unknown as { fullCatalog: () => unknown[] }
    ).fullCatalog();
    expect(fullCatalog).toEqual([]);
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

  const problem = (status: number, errors?: { field: string; message: string; code: string }[]) =>
    new HttpErrorResponse({
      status,
      error: { type: 'about:blank', title: 'x', status, errors },
    });

  async function readyToSubmit() {
    const fixture = TestBed.createComponent(ImagingOrderWizardPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const page = fixture.componentInstance;
    const exam = page['fullCatalog']().find((e) => !e.requiresLaterality)!;
    page['step1Form'].controls.modality.setValue(exam.modality);
    page['step1Form'].controls.examCode.setValue(exam.code);
    page['step2Form'].controls.clinicalIndication.setValue(
      'Duszność wysiłkowa od tygodnia, podejrzenie zapalenia płuc.',
    );
    page['step3Form'].controls.confirmed.setValue(true);
    return { fixture, page, exam };
  }

  it('submits the exam code and the selected slot', async () => {
    const service = TestBed.inject(ImagingOrderService);
    const create = vi.spyOn(service, 'createOrder');
    const { page, exam } = await readyToSubmit();
    page['onSlotSelected']({ id: 'slot-1', start: '2026-10-05T08:00:00Z' } as ScheduleSlot);
    page['submit']();
    expect(create).toHaveBeenCalledTimes(1);
    const body = create.mock.calls[0][0];
    expect(body.examCode).toBe(exam.code);
    expect(body.slotId).toBe('slot-1');
    expect(body.patientId).toBe('pat-001');
  });

  it('maps 422 errors[] onto the form controls', async () => {
    const service = TestBed.inject(ImagingOrderService);
    const { page } = await readyToSubmit();
    vi.spyOn(service, 'createOrder').mockReturnValue(
      throwError(() =>
        problem(422, [
          { field: 'examCode', message: 'Badanie nie istnieje', code: 'notFound' },
          { field: 'safety.confirmed', message: 'Wymagane', code: 'required' },
        ]),
      ),
    );
    page['submit']();
    expect(page['step1Form'].controls.examCode.errors).toEqual({ server: 'Badanie nie istnieje' });
    expect(page['step3Form'].controls.confirmed.errors).toEqual({ server: 'Wymagane' });
    expect(page['submitting']()).toBe(false);
  });

  it('drops the chosen slot and refreshes the picker on a 409 slot conflict', async () => {
    const service = TestBed.inject(ImagingOrderService);
    const { page } = await readyToSubmit();
    page['onSlotSelected']({ id: 'slot-1', start: '2026-10-05T08:00:00Z' } as ScheduleSlot);
    const before = page['slotRefresh']();
    vi.spyOn(service, 'createOrder').mockReturnValue(throwError(() => problem(409)));
    page['submit']();
    expect(page['selectedSlot']()).toBeNull();
    expect(page['step4Form'].controls.slotId.value).toBeNull();
    expect(page['slotRefresh']()).toBe(before + 1);
  });

  it('marks the wizard as submitted once the order is created', async () => {
    const service = TestBed.inject(ImagingOrderService);
    const { page } = await readyToSubmit();
    vi.spyOn(service, 'createOrder').mockReturnValue(of({} as ImagingOrder));
    page['submit']();
    expect(page['submitted']()).toBe(true);
  });

  describe('fail-closed: contrast safety data unavailable', () => {
    async function setUpWithContrastExam(options: { contrast: boolean }) {
      const fixture = TestBed.createComponent(ImagingOrderWizardPage);
      fixture.componentRef.setInput('patientId', 'pat-001');
      await fixture.whenStable();
      const page = fixture.componentInstance;
      const exam = page['fullCatalog']().find((e) => e.contrastPossible && !e.requiresLaterality)!;
      page['step1Form'].controls.modality.setValue(exam.modality);
      page['step1Form'].controls.examCode.setValue(exam.code);
      page['step1Form'].controls.contrast.setValue(options.contrast);
      page['step2Form'].controls.clinicalIndication.setValue(
        'Duszność wysiłkowa od tygodnia, podejrzenie zapalenia płuc.',
      );
      page['step3Form'].controls.confirmed.setValue(true);
      return { fixture, page, exam };
    }

    it('(a) does not block an order WITHOUT contrast even when allergy/lab data failed to load', async () => {
      const ehrService = TestBed.inject(EhrService);
      vi.spyOn(ehrService, 'getAllergies').mockReturnValue(throwError(() => new Error('500')));
      const { page } = await setUpWithContrastExam({ contrast: false });
      expect(page['contrastSafetyUnverified']()).toBe(false);
      expect(page['step3Blocked']()).toBe(false);

      let activated: number | null = null;
      page['advanceFromStep3']((v: number) => (activated = v));
      expect(activated).toBe(4);

      const service = TestBed.inject(ImagingOrderService);
      const create = vi.spyOn(service, 'createOrder').mockReturnValue(of({} as ImagingOrder));
      page['submit']();
      expect(create).toHaveBeenCalledTimes(1);
    });

    it('(b) blocks an order WITH contrast when allergy data failed and contrastAllergy is not checked', async () => {
      const ehrService = TestBed.inject(EhrService);
      vi.spyOn(ehrService, 'getAllergies').mockReturnValue(throwError(() => new Error('500')));
      const { page } = await setUpWithContrastExam({ contrast: true });
      expect(page['contrastSafetyUnverified']()).toBe(true);
      expect(page['step3Blocked']()).toBe(true);

      let activated: number | null = null;
      page['advanceFromStep3']((v: number) => (activated = v));
      expect(activated).toBeNull();

      const service = TestBed.inject(ImagingOrderService);
      const create = vi.spyOn(service, 'createOrder');
      page['submit']();
      expect(create).not.toHaveBeenCalled();
    });

    it('(b) blocks an order WITH contrast when lab results failed to load and contrastAllergy is not checked', async () => {
      const labResultService = TestBed.inject(LabResultService);
      vi.spyOn(labResultService, 'getResults').mockReturnValue(throwError(() => new Error('500')));
      const { page } = await setUpWithContrastExam({ contrast: true });
      expect(page['contrastSafetyUnverified']()).toBe(true);
      expect(page['step3Blocked']()).toBe(true);

      const service = TestBed.inject(ImagingOrderService);
      const create = vi.spyOn(service, 'createOrder');
      page['submit']();
      expect(create).not.toHaveBeenCalled();
    });

    it('(c) allows an order WITH contrast when data loads fine and there is no allergy', async () => {
      const { page } = await setUpWithContrastExam({ contrast: true });
      expect(page['contrastSafetyUnverified']()).toBe(false);
      expect(page['step3Blocked']()).toBe(false);

      const service = TestBed.inject(ImagingOrderService);
      const create = vi.spyOn(service, 'createOrder').mockReturnValue(of({} as ImagingOrder));
      page['submit']();
      expect(create).toHaveBeenCalledTimes(1);
    });

    it('(d) allows an order WITH contrast when data failed to load BUT contrastAllergy is manually checked', async () => {
      const ehrService = TestBed.inject(EhrService);
      vi.spyOn(ehrService, 'getAllergies').mockReturnValue(throwError(() => new Error('500')));
      const { page } = await setUpWithContrastExam({ contrast: true });
      page['step3Form'].controls.contrastAllergy.setValue(true);
      expect(page['contrastSafetyUnverified']()).toBe(false);
      expect(page['step3Blocked']()).toBe(false);

      let activated: number | null = null;
      page['advanceFromStep3']((v: number) => (activated = v));
      expect(activated).toBe(4);

      const service = TestBed.inject(ImagingOrderService);
      const create = vi.spyOn(service, 'createOrder').mockReturnValue(of({} as ImagingOrder));
      page['submit']();
      expect(create).toHaveBeenCalledTimes(1);
    });
  });
});
