import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { LAB_CATALOG, LAB_PANELS } from '../mock-data/lab-catalog.mock';
import { LAB_ORDERS } from '../mock-data/lab-orders.mock';
import type { ID, LabOrder, LabOrderDraft, LabTest, OrderStatus, OrderUrgency } from '../models';
import { mockError, mockResponse, nextId } from '../utils/mock-response';

@Injectable({ providedIn: 'root' })
export class LabOrderService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly catalog: LabTest[] = structuredClone(LAB_CATALOG);
  private readonly panels = structuredClone(LAB_PANELS);
  private readonly orders: LabOrder[] = structuredClone(LAB_ORDERS);
  private sequence = this.orders.length;

  getCatalog(): Observable<LabTest[]> {
    return mockResponse(this.catalog, this.latency);
  }

  getPanels(): Observable<{ id: string; name: string; testCodes: string[] }[]> {
    return mockResponse(this.panels, this.latency);
  }

  getOrders(filter?: {
    patientId?: ID;
    status?: OrderStatus;
    urgency?: OrderUrgency;
  }): Observable<LabOrder[]> {
    let result = this.orders;
    if (filter?.patientId) result = result.filter((o) => o.patientId === filter.patientId);
    if (filter?.status) result = result.filter((o) => o.status === filter.status);
    if (filter?.urgency) result = result.filter((o) => o.urgency === filter.urgency);
    return mockResponse(result, this.latency);
  }

  getOrderById(id: ID): Observable<LabOrder> {
    const found = this.orders.find((o) => o.id === id);
    if (!found) return mockError(`Nie znaleziono zlecenia o id ${id}`, this.latency);
    return mockResponse(found, this.latency);
  }

  createOrder(draft: LabOrderDraft): Observable<LabOrder> {
    this.sequence++;
    const now = new Date().toISOString();
    const order: LabOrder = {
      ...draft,
      id: nextId('lord', this.sequence),
      orderedAt: now,
      status: 'ordered',
      statusHistory: [{ status: 'ordered', at: now, byId: draft.orderedById }],
    };
    this.orders.push(order);
    return mockResponse(order, this.latency);
  }

  updateStatus(id: ID, status: OrderStatus, note?: string): Observable<LabOrder> {
    const index = this.orders.findIndex((o) => o.id === id);
    if (index === -1) return mockError(`Nie znaleziono zlecenia o id ${id}`, this.latency);
    const now = new Date().toISOString();
    const updated: LabOrder = {
      ...this.orders[index],
      status,
      statusHistory: [...this.orders[index].statusHistory, { status, at: now, note }],
    };
    this.orders[index] = updated;
    return mockResponse(updated, this.latency);
  }

  cancelOrder(id: ID, reason: string): Observable<LabOrder> {
    return this.updateStatus(id, 'cancelled', reason);
  }
}
