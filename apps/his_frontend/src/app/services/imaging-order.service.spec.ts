import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import {
  IMAGING_EXAMS_URL,
  IMAGING_ORDERS_URL,
  IMAGING_SLOTS_URL,
  imagingOrderCancelUrl,
  imagingOrderStatusUrl,
  imagingOrderUrl,
  patientImagingOrdersUrl,
} from '../config/api.config';
import { IMAGING_CATALOG } from '../mock-data/imaging-catalog.mock';
import { IMAGING_ORDERS } from '../mock-data/imaging-orders.mock';
import { generateSlots } from '../mock-data/schedule-slots.mock';
import type { ImagingOrder, ImagingOrderDraft } from '../models';
import type { Page } from '../models/api';
import { ImagingOrderService } from './imaging-order.service';

const page = (items: ImagingOrder[], n: number, totalPages: number): Page<ImagingOrder> => ({
  items,
  page: n,
  size: 100,
  totalElements: items.length,
  totalPages,
});

function draft(slotId?: string): ImagingOrderDraft {
  return {
    patientId: 'pat-003',
    examCode: 'USG-JB',
    examName: 'USG jamy brzusznej',
    modality: 'USG',
    bodyRegion: 'Jama brzuszna',
    laterality: 'na',
    contrast: false,
    clinicalIndication: 'Test',
    urgency: 'routine',
    safety: {
      pregnancy: 'na',
      pacemakerOrImplant: false,
      metalFragments: false,
      contrastAllergy: false,
      claustrophobia: false,
      confirmed: true,
    },
    slotId,
    orderedById: 'stf-001',
  };
}

describe('ImagingOrderService', () => {
  let service: ImagingOrderService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ImagingOrderService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getCatalog GETs /imaging-exams without a modality by default', async () => {
    const result = firstValueFrom(service.getCatalog());
    const req = http.expectOne((r) => r.url === IMAGING_EXAMS_URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush(IMAGING_CATALOG);
    expect(await result).toEqual(IMAGING_CATALOG);
  });

  it('getCatalog sends the modality filter', () => {
    service.getCatalog('CT').subscribe();
    const req = http.expectOne((r) => r.url === IMAGING_EXAMS_URL);
    expect(req.request.params.get('modality')).toBe('CT');
    req.flush([]);
  });

  it('getCatalog surfaces 422 for a bad modality', async () => {
    const result = firstValueFrom(service.getCatalog('CT'));
    http
      .expectOne((r) => r.url === IMAGING_EXAMS_URL)
      .flush(null, { status: 422, statusText: 'Unprocessable Entity' });
    await expect(result).rejects.toMatchObject({ status: 422 });
  });

  it('getSlots sends modality and date', async () => {
    const slots = generateSlots('USG', '2026-06-01');
    const result = firstValueFrom(service.getSlots('USG', '2026-06-01'));
    const req = http.expectOne((r) => r.url === IMAGING_SLOTS_URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('modality')).toBe('USG');
    expect(req.request.params.get('date')).toBe('2026-06-01');
    req.flush(slots);
    expect(await result).toEqual(slots);
  });

  it('getOrders sends every filter as a query parameter', async () => {
    const result = firstValueFrom(
      service.getOrders({
        patientId: 'pat-001',
        status: 'scheduled',
        urgency: 'urgent',
        modality: 'MRI',
      }),
    );
    const req = http.expectOne((r) => r.url === IMAGING_ORDERS_URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('patientId')).toBe('pat-001');
    expect(req.request.params.get('status')).toBe('scheduled');
    expect(req.request.params.get('urgency')).toBe('urgent');
    expect(req.request.params.get('modality')).toBe('MRI');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('100');
    req.flush(page([IMAGING_ORDERS[0]], 0, 1));
    expect(await result).toEqual([IMAGING_ORDERS[0]]);
  });

  it('getOrders omits absent filters', () => {
    service.getOrders().subscribe();
    const req = http.expectOne((r) => r.url === IMAGING_ORDERS_URL);
    expect(req.request.params.keys().sort()).toEqual(['page', 'size']);
    req.flush(page([], 0, 0));
  });

  it('getOrders reads every page', async () => {
    const result = firstValueFrom(service.getOrders());
    http
      .expectOne((r) => r.url === IMAGING_ORDERS_URL && r.params.get('page') === '0')
      .flush(page([IMAGING_ORDERS[0]], 0, 2));
    http
      .expectOne((r) => r.url === IMAGING_ORDERS_URL && r.params.get('page') === '1')
      .flush(page([IMAGING_ORDERS[1]], 1, 2));
    expect(await result).toEqual([IMAGING_ORDERS[0], IMAGING_ORDERS[1]]);
  });

  it('getOrderById GETs /imaging-orders/{id}', async () => {
    const result = firstValueFrom(service.getOrderById('iord-001'));
    http.expectOne({ method: 'GET', url: imagingOrderUrl('iord-001') }).flush(IMAGING_ORDERS[0]);
    expect(await result).toEqual(IMAGING_ORDERS[0]);
  });

  it('createOrder without a slot POSTs under the patient and returns an ordered order', async () => {
    const created: ImagingOrder = { ...IMAGING_ORDERS[0], id: 'new-id', status: 'ordered' };
    const body = draft();
    const result = firstValueFrom(service.createOrder(body));
    const req = http.expectOne({ method: 'POST', url: patientImagingOrdersUrl('pat-003') });
    expect(req.request.body).toEqual(body);
    req.flush(created, { status: 201, statusText: 'Created' });
    expect((await result).status).toBe('ordered');
  });

  it('createOrder with a slot returns an order that is scheduled at once', async () => {
    const created: ImagingOrder = {
      ...IMAGING_ORDERS[0],
      id: 'new-id',
      status: 'scheduled',
      slotId: 'slot-1',
    };
    const result = firstValueFrom(service.createOrder(draft('slot-1')));
    const req = http.expectOne({ method: 'POST', url: patientImagingOrdersUrl('pat-003') });
    expect(req.request.body.slotId).toBe('slot-1');
    req.flush(created, { status: 201, statusText: 'Created' });
    expect((await result).status).toBe('scheduled');
  });

  it('createOrder surfaces 422 field errors (unknown exam/slot, safety)', async () => {
    const result = firstValueFrom(service.createOrder(draft('slot-x')));
    http.expectOne(patientImagingOrdersUrl('pat-003')).flush(
      {
        type: 'about:blank',
        title: 'Validation failed',
        status: 422,
        code: 'VALIDATION_FAILED',
        errors: [
          { field: 'examCode', message: 'nie istnieje', code: 'notFound' },
          { field: 'slotId', message: 'nie istnieje', code: 'notFound' },
          { field: 'safety.confirmed', message: 'wymagane', code: 'required' },
        ],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    await expect(result).rejects.toMatchObject({
      status: 422,
      error: {
        errors: [{ field: 'examCode' }, { field: 'slotId' }, { field: 'safety.confirmed' }],
      },
    });
  });

  it('createOrder surfaces 409 for a taken slot', async () => {
    const result = firstValueFrom(service.createOrder(draft('slot-1')));
    http
      .expectOne(patientImagingOrdersUrl('pat-003'))
      .flush(null, { status: 409, statusText: 'Conflict' });
    await expect(result).rejects.toMatchObject({ status: 409 });
  });

  it('updateStatus POSTs status, note and version', async () => {
    const result = firstValueFrom(service.updateStatus('iord-005', 'in_progress', 'ok', 2));
    const req = http.expectOne({ method: 'POST', url: imagingOrderStatusUrl('iord-005') });
    expect(req.request.body).toEqual({ status: 'in_progress', note: 'ok', version: 2 });
    req.flush({ ...IMAGING_ORDERS[0], status: 'in_progress' });
    expect((await result).status).toBe('in_progress');
  });

  it('updateStatus surfaces 409 for a forbidden transition', async () => {
    const result = firstValueFrom(service.updateStatus('iord-005', 'scheduled'));
    http
      .expectOne(imagingOrderStatusUrl('iord-005'))
      .flush(null, { status: 409, statusText: 'Conflict' });
    await expect(result).rejects.toMatchObject({ status: 409 });
  });

  it('cancelOrder POSTs reason and version', async () => {
    const result = firstValueFrom(service.cancelOrder('iord-005', 'Pacjent zrezygnował', 4));
    const req = http.expectOne({ method: 'POST', url: imagingOrderCancelUrl('iord-005') });
    expect(req.request.body).toEqual({ reason: 'Pacjent zrezygnował', version: 4 });
    req.flush({ ...IMAGING_ORDERS[0], status: 'cancelled' });
    expect((await result).status).toBe('cancelled');
  });
});
