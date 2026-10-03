import { of, throwError } from 'rxjs';
import { LAB_CATALOG, LAB_PANELS } from '../mock-data/lab-catalog.mock';
import { LAB_ORDERS } from '../mock-data/lab-orders.mock';
import type { LabOrder, OrderStatus } from '../models';
import type { LabOrderCreateRequest, LabOrderFilter } from '../models/api';
import { LabOrderService } from '../services/lab-order.service';

/** In-memory `LabOrderService` double over the mock lab orders (fresh state per TestBed). */
export function createLabOrderServiceStub(): Partial<Record<keyof LabOrderService, unknown>> {
  const orders: LabOrder[] = structuredClone(LAB_ORDERS);
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
    getCatalog: () => of(structuredClone(LAB_CATALOG)),
    getPanels: () => of(structuredClone(LAB_PANELS)),
    getOrders: (filter?: LabOrderFilter) =>
      of(
        orders.filter(
          (o) =>
            (!filter?.patientId || o.patientId === filter.patientId) &&
            (!filter?.status || o.status === filter.status) &&
            (!filter?.urgency || o.urgency === filter.urgency),
        ),
      ),
    getOrderById: (id: string) => {
      const found = orders.find((o) => o.id === id);
      return found ? of(found) : notFound(id);
    },
    createOrder: (draft: LabOrderCreateRequest) => {
      const at = new Date().toISOString();
      const order: LabOrder = {
        ...draft,
        id: `test-lord-${orders.length + 1}`,
        orderedById: 'test-staff',
        orderedAt: at,
        status: 'ordered',
        statusHistory: [{ status: 'ordered', at, byId: 'test-staff' }],
      };
      orders.push(order);
      return of(order);
    },
    updateStatus: change,
    cancelOrder: (id: string, reason: string) => change(id, 'cancelled', reason),
  };
}

/** Test provider replacing the HTTP-backed `LabOrderService` with the mock lab orders. */
export const labOrderServiceStub = {
  provide: LabOrderService,
  useFactory: createLabOrderServiceStub,
};
