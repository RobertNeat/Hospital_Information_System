import { of, throwError } from 'rxjs';
import { IMAGING_CATALOG } from '../mock-data/imaging-catalog.mock';
import { IMAGING_ORDERS } from '../mock-data/imaging-orders.mock';
import { generateSlots } from '../mock-data/schedule-slots.mock';
import type { ImagingModality, ImagingOrder, ISODate, OrderStatus } from '../models';
import type { ImagingOrderCreateRequest, ImagingOrderFilter } from '../models/api';
import { ImagingOrderService } from '../services/imaging-order.service';

/** In-memory `ImagingOrderService` double over the mock imaging orders (fresh state per TestBed). */
export function createImagingOrderServiceStub(): Partial<
  Record<keyof ImagingOrderService, unknown>
> {
  const orders: ImagingOrder[] = structuredClone(IMAGING_ORDERS);
  const notFound = (id: string) => throwError(() => new Error(`Nie znaleziono zlecenia ${id}`));
  const change = (id: string, status: OrderStatus, note?: string) => {
    const index = orders.findIndex((o) => o.id === id);
    if (index === -1) return notFound(id);
    const at = new Date().toISOString();
    orders[index] = {
      ...orders[index],
      status,
      statusHistory: [...orders[index].statusHistory, { status, at, note }],
    };
    return of(orders[index]);
  };

  return {
    getCatalog: (modality?: ImagingModality) =>
      of(structuredClone(IMAGING_CATALOG.filter((e) => !modality || e.modality === modality))),
    getSlots: (modality: ImagingModality, date: ISODate) => of(generateSlots(modality, date)),
    getOrders: (filter?: ImagingOrderFilter) =>
      of(
        orders.filter(
          (o) =>
            (!filter?.patientId || o.patientId === filter.patientId) &&
            (!filter?.status || o.status === filter.status) &&
            (!filter?.urgency || o.urgency === filter.urgency) &&
            (!filter?.modality || o.modality === filter.modality),
        ),
      ),
    getOrderById: (id: string) => {
      const found = orders.find((o) => o.id === id);
      return found ? of(found) : notFound(id);
    },
    createOrder: (draft: ImagingOrderCreateRequest) => {
      const at = new Date().toISOString();
      const status: OrderStatus = draft.slotId ? 'scheduled' : 'ordered';
      const order: ImagingOrder = {
        ...draft,
        id: `test-iord-${orders.length + 1}`,
        orderedAt: at,
        status,
        statusHistory: [{ status, at, byId: draft.orderedById }],
      };
      orders.push(order);
      return of(order);
    },
    updateStatus: change,
    cancelOrder: (id: string, reason: string) => change(id, 'cancelled', reason),
  };
}

/** Test provider replacing the HTTP-backed `ImagingOrderService` with the mock imaging orders. */
export const imagingOrderServiceStub = {
  provide: ImagingOrderService,
  useFactory: createImagingOrderServiceStub,
};
