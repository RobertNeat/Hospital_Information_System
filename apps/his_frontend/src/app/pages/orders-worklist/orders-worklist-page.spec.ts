import { imagingOrderServiceStub } from '../../testing/imaging-order-service.stub';
import { labOrderServiceStub } from '../../testing/lab-order-service.stub';
import { patientServiceStub } from '../../testing/patient-service.stub';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../services/auth.service';
import { LabOrderService } from '../../services/lab-order.service';
import type { OrderStatus } from '../../models';
import { OrdersWorklistPage } from './orders-worklist-page';

describe('OrdersWorklistPage', () => {
  let permissions: string[];
  const statusValues = (
    page: OrdersWorklistPage,
    row: { type: 'lab' | 'imaging'; status: OrderStatus },
  ) =>
    (
      page as unknown as {
        nextStatusOptions: (r: unknown) => { value: OrderStatus }[];
      }
    )
      .nextStatusOptions(row)
      .map((o) => o.value);

  beforeEach(() => {
    permissions = [
      'imaging-order:update-status',
      'lab-order:update-status',
      'imaging-order:read',
      'lab-order:read',
    ];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: { hasPermission: (p: string) => permissions.includes(p) },
        },
        patientServiceStub,
        ehrServiceStub,
        wardServiceStub,
        provideRouter([]),
        MessageService,
        labOrderServiceStub,
        imagingOrderServiceStub,
      ],
    });
  });

  it('renders the page header and worklist rows once loaded', async () => {
    const fixture = TestBed.createComponent(OrdersWorklistPage);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Zlecenia');
    expect(text).toContain('Zlecone:');
  });

  it('filters rows by the type query param', async () => {
    const fixture = TestBed.createComponent(OrdersWorklistPage);
    fixture.componentRef.setInput('type', 'lab');
    await fixture.whenStable();
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    expect(rows.length).toBeGreaterThan(0);
  });

  it('offers imaging transitions of the backend state machine (no specimen_collected, no cancel)', async () => {
    const fixture = TestBed.createComponent(OrdersWorklistPage);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    expect(statusValues(page, { type: 'imaging', status: 'ordered' })).toEqual([
      'scheduled',
      'in_progress',
    ]);
    expect(statusValues(page, { type: 'imaging', status: 'scheduled' })).toEqual([
      'in_progress',
      'completed',
    ]);
    expect(statusValues(page, { type: 'imaging', status: 'in_progress' })).toEqual(['completed']);
    expect(statusValues(page, { type: 'imaging', status: 'completed' })).toEqual([]);
    expect(statusValues(page, { type: 'imaging', status: 'cancelled' })).toEqual([]);
  });

  it('offers no imaging transitions without the update-status permission', async () => {
    permissions = [];
    const fixture = TestBed.createComponent(OrdersWorklistPage);
    await fixture.whenStable();
    expect(statusValues(fixture.componentInstance, { type: 'imaging', status: 'ordered' })).toEqual(
      [],
    );
  });

  it('never requests lab orders and shows no error toast for a role without lab-order:read', async () => {
    permissions = ['imaging-order:read'];
    const lab = TestBed.inject(LabOrderService);
    const getOrdersSpy = vi.spyOn(lab, 'getOrders');
    const toast = TestBed.inject(MessageService);
    const addSpy = vi.spyOn(toast, 'add');

    const fixture = TestBed.createComponent(OrdersWorklistPage);
    await fixture.whenStable();

    expect(getOrdersSpy).not.toHaveBeenCalled();
    expect(addSpy).not.toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    expect(rows.length).toBeGreaterThan(0);
    rows.forEach((row) => {
      expect(row.textContent).toContain('Obrazowe');
    });
  });

  it('offers lab transitions with update-status, only specimen_collected for a nurse, none otherwise', async () => {
    const fixture = TestBed.createComponent(OrdersWorklistPage);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    expect(statusValues(page, { type: 'lab', status: 'ordered' })).toEqual([
      'scheduled',
      'specimen_collected',
    ]);
    permissions = ['lab-order:collect-specimen'];
    expect(statusValues(page, { type: 'lab', status: 'ordered' })).toEqual(['specimen_collected']);
    expect(statusValues(page, { type: 'lab', status: 'specimen_collected' })).toEqual([]);
    permissions = [];
    expect(statusValues(page, { type: 'lab', status: 'ordered' })).toEqual([]);
  });
});
