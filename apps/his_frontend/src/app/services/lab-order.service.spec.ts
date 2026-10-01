import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import {
  LAB_ORDERS_URL,
  LAB_PANELS_URL,
  LAB_TESTS_URL,
  labOrderCancelUrl,
  labOrderStatusUrl,
  labOrderUrl,
  patientLabOrdersUrl,
} from '../config/api.config';
import { LAB_CATALOG, LAB_PANELS } from '../mock-data/lab-catalog.mock';
import { LAB_ORDERS } from '../mock-data/lab-orders.mock';
import type { LabOrder, LabOrderDraft } from '../models';
import type { Page } from '../models/api';
import { LabOrderService } from './lab-order.service';

const page = (items: LabOrder[], n: number, totalPages: number): Page<LabOrder> => ({
  items,
  page: n,
  size: 100,
  totalElements: items.length,
  totalPages,
});

function draft(): LabOrderDraft {
  return {
    patientId: 'pat-003',
    orderedById: 'stf-001',
    items: [{ testCode: 'MORF', testName: 'Morfologia krwi z rozmazem', specimenType: 'blood' }],
    urgency: 'routine',
    fasting: false,
    plannedCollectionAt: '2026-01-01T08:00:00.000Z',
    clinicalInfo: 'Kontrola.',
  };
}

describe('LabOrderService', () => {
  let service: LabOrderService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LabOrderService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getCatalog GETs /lab-tests', async () => {
    const result = firstValueFrom(service.getCatalog());
    http.expectOne({ method: 'GET', url: LAB_TESTS_URL }).flush(LAB_CATALOG);
    expect(await result).toEqual(LAB_CATALOG);
  });

  it('getPanels GETs /lab-panels', async () => {
    const result = firstValueFrom(service.getPanels());
    http.expectOne({ method: 'GET', url: LAB_PANELS_URL }).flush(LAB_PANELS);
    expect(await result).toEqual(LAB_PANELS);
  });

  it('getOrders sends every filter as a query parameter', async () => {
    const result = firstValueFrom(
      service.getOrders({
        patientId: 'pat-001',
        status: 'completed',
        urgency: 'stat',
        orderedFrom: '2026-01-01T00:00:00Z',
        orderedTo: new Date('2026-02-01T00:00:00Z'),
      }),
    );
    const req = http.expectOne((r) => r.url === LAB_ORDERS_URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('patientId')).toBe('pat-001');
    expect(req.request.params.get('status')).toBe('completed');
    expect(req.request.params.get('urgency')).toBe('stat');
    expect(req.request.params.get('orderedFrom')).toBe('2026-01-01T00:00:00Z');
    expect(req.request.params.get('orderedTo')).toBe('2026-02-01T00:00:00.000Z');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('100');
    req.flush(page([LAB_ORDERS[0]], 0, 1));
    expect(await result).toEqual([LAB_ORDERS[0]]);
  });

  it('getOrders omits absent filters', () => {
    service.getOrders().subscribe();
    const req = http.expectOne((r) => r.url === LAB_ORDERS_URL);
    expect(req.request.params.keys().sort()).toEqual(['page', 'size']);
    req.flush(page([], 0, 0));
  });

  it('getOrders reads every page', async () => {
    const result = firstValueFrom(service.getOrders());
    http
      .expectOne((r) => r.url === LAB_ORDERS_URL && r.params.get('page') === '0')
      .flush(page([LAB_ORDERS[0]], 0, 2));
    http
      .expectOne((r) => r.url === LAB_ORDERS_URL && r.params.get('page') === '1')
      .flush(page([LAB_ORDERS[1]], 1, 2));
    expect(await result).toEqual([LAB_ORDERS[0], LAB_ORDERS[1]]);
  });

  it('getOrderById GETs /lab-orders/{id}', async () => {
    const result = firstValueFrom(service.getOrderById('lord-001'));
    http.expectOne({ method: 'GET', url: labOrderUrl('lord-001') }).flush(LAB_ORDERS[0]);
    expect(await result).toEqual(LAB_ORDERS[0]);
  });

  it('createOrder POSTs under the patient and returns the created order', async () => {
    const created: LabOrder = { ...LAB_ORDERS[0], id: 'new-id', status: 'ordered' };
    const body = draft();
    const result = firstValueFrom(service.createOrder(body));
    const req = http.expectOne({ method: 'POST', url: patientLabOrdersUrl('pat-003') });
    expect(req.request.body).toEqual(body);
    req.flush(created, { status: 201, statusText: 'Created' });
    expect(await result).toEqual(created);
  });

  it('createOrder surfaces 422 field errors (unknown test, fasting rule)', async () => {
    const result = firstValueFrom(service.createOrder(draft()));
    http.expectOne(patientLabOrdersUrl('pat-003')).flush(
      {
        type: 'about:blank',
        title: 'Validation failed',
        status: 422,
        code: 'VALIDATION_FAILED',
        errors: [
          { field: 'items[0].testCode', message: 'nie istnieje', code: 'notFound' },
          { field: 'fasting', message: 'wymagane na czczo', code: 'fastingRequired' },
        ],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    await expect(result).rejects.toMatchObject({
      status: 422,
      error: { errors: [{ code: 'notFound' }, { code: 'fastingRequired' }] },
    });
  });

  it('updateStatus POSTs status, note and version', async () => {
    const result = firstValueFrom(service.updateStatus('lord-006', 'specimen_collected', 'ok', 2));
    const req = http.expectOne({ method: 'POST', url: labOrderStatusUrl('lord-006') });
    expect(req.request.body).toEqual({ status: 'specimen_collected', note: 'ok', version: 2 });
    req.flush({ ...LAB_ORDERS[0], status: 'specimen_collected' });
    expect((await result).status).toBe('specimen_collected');
  });

  it('updateStatus surfaces 409 for a forbidden transition', async () => {
    const result = firstValueFrom(service.updateStatus('lord-006', 'completed'));
    http
      .expectOne(labOrderStatusUrl('lord-006'))
      .flush(null, { status: 409, statusText: 'Conflict' });
    await expect(result).rejects.toMatchObject({ status: 409 });
  });

  it('cancelOrder POSTs reason and version', async () => {
    const result = firstValueFrom(service.cancelOrder('lord-006', 'Pacjent zrezygnował', 4));
    const req = http.expectOne({ method: 'POST', url: labOrderCancelUrl('lord-006') });
    expect(req.request.body).toEqual({ reason: 'Pacjent zrezygnował', version: 4 });
    req.flush({ ...LAB_ORDERS[0], status: 'cancelled' });
    expect((await result).status).toBe('cancelled');
  });
});
