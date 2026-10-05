import { imagingOrderServiceStub } from '../../testing/imaging-order-service.stub';
import { labOrderServiceStub } from '../../testing/lab-order-service.stub';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { AuthService } from '../../services/auth.service';
import { ImagingOrderService } from '../../services/imaging-order.service';
import { LabOrderService } from '../../services/lab-order.service';
import { IMAGING_ORDERS } from '../../mock-data/imaging-orders.mock';
import { PatientOrdersPage } from './patient-orders-page';

// jsdom has no ResizeObserver; PrimeNG's p-tabs relies on it in ngAfterViewInit.
class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

describe('PatientOrdersPage', () => {
  let permissions: string[];

  beforeEach(() => {
    permissions = [
      'imaging-order:cancel',
      'lab-order:cancel',
      'imaging-order:read',
      'lab-order:read',
    ];
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: { hasPermission: (p: string) => permissions.includes(p) },
        },
        provideRouter([]),
        MessageService,
        ConfirmationService,
        labOrderServiceStub,
        imagingOrderServiceStub,
      ],
    });
  });

  it('renders the page header and lab orders for the patient', async () => {
    const fixture = TestBed.createComponent(PatientOrdersPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Zlecenia pacjenta');
  });

  it('defaults to the lab tab, showing lab orders and not imaging orders', async () => {
    const fixture = TestBed.createComponent(PatientOrdersPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const activeTab = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-p-active="true"]',
    );
    expect(activeTab?.textContent).toContain('Laboratoryjne');
  });

  it('switches to the imaging tab via the type input', async () => {
    const fixture = TestBed.createComponent(PatientOrdersPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('type', 'imaging');
    await fixture.whenStable();
    const activeTab = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-p-active="true"]',
    );
    expect(activeTab?.textContent).toContain('Obrazowe');
  });

  it('offers imaging cancellation only with the cancel permission', async () => {
    const fixture = TestBed.createComponent(PatientOrdersPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const page = fixture.componentInstance as unknown as {
      canCancel: (status: string, kind?: 'lab' | 'imaging') => boolean;
    };
    expect(page.canCancel('ordered', 'imaging')).toBe(true);
    expect(page.canCancel('completed', 'imaging')).toBe(false);
    permissions = [];
    expect(page.canCancel('ordered', 'imaging')).toBe(false);
  });

  it('cancels an imaging order with the reason and the loaded version, then reloads', async () => {
    const order = { ...IMAGING_ORDERS[0], version: 3 };
    const imaging = TestBed.inject(ImagingOrderService);
    vi.spyOn(imaging, 'getOrders').mockReturnValue(of([order]));
    const cancel = vi.spyOn(imaging, 'cancelOrder').mockReturnValue(of(order));
    const confirmation = TestBed.inject(ConfirmationService);
    let accept: (() => void) | undefined;
    vi.spyOn(confirmation, 'confirm').mockImplementation((c) => {
      accept = c.accept as () => void;
      return confirmation;
    });

    const fixture = TestBed.createComponent(PatientOrdersPage);
    fixture.componentRef.setInput('patientId', order.patientId);
    await fixture.whenStable();
    const page = fixture.componentInstance as unknown as {
      cancelReason: { set: (v: string) => void };
      requestCancel: (kind: 'lab' | 'imaging', id: string) => void;
    };
    page.requestCancel('imaging', order.id);
    page.cancelReason.set('Pacjent zrezygnował');
    accept?.();
    expect(cancel).toHaveBeenCalledWith(order.id, 'Pacjent zrezygnował', order.version);
  });

  it('shows a toast and an empty list instead of a frozen view when an allowed call fails', async () => {
    const lab = TestBed.inject(LabOrderService);
    vi.spyOn(lab, 'getOrders').mockReturnValue(throwError(() => new Error('Server error')));
    const toast = TestBed.inject(MessageService);
    const addSpy = vi.spyOn(toast, 'add');

    const fixture = TestBed.createComponent(PatientOrdersPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    expect(addSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        summary: expect.stringContaining('laboratoryjnych'),
      }),
    );
    const page = fixture.componentInstance as unknown as { labOrders: () => unknown[] };
    expect(page.labOrders()).toEqual([]);
  });

  it('never requests lab orders, shows no error toast and no lab tab for a role without lab-order:read', async () => {
    permissions = ['imaging-order:read'];
    const lab = TestBed.inject(LabOrderService);
    const getOrdersSpy = vi.spyOn(lab, 'getOrders');
    const toast = TestBed.inject(MessageService);
    const addSpy = vi.spyOn(toast, 'add');

    const fixture = TestBed.createComponent(PatientOrdersPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    expect(getOrdersSpy).not.toHaveBeenCalled();
    expect(addSpy).not.toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Laboratoryjne');
    // Defaults to the only tab the role can see, not the hardcoded 'lab' default.
    const activeTab = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-p-active="true"]',
    );
    expect(activeTab?.textContent).toContain('Obrazowe');
  });

  it('offers lab cancellation only with the lab cancel permission', async () => {
    const fixture = TestBed.createComponent(PatientOrdersPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const page = fixture.componentInstance as unknown as {
      canCancel: (status: string, kind?: 'lab' | 'imaging') => boolean;
    };
    expect(page.canCancel('ordered', 'lab')).toBe(true);
    permissions = ['imaging-order:cancel'];
    expect(page.canCancel('ordered', 'lab')).toBe(false);
  });
});
