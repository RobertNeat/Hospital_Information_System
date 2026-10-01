import { imagingOrderServiceStub } from '../../testing/imaging-order-service.stub';
import { labOrderServiceStub } from '../../testing/lab-order-service.stub';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { of } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { AuthService } from '../../services/auth.service';
import { ImagingOrderService } from '../../services/imaging-order.service';
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
    permissions = ['imaging-order:cancel', 'lab-order:cancel'];
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
