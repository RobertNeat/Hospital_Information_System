import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { IMAGING_CATALOG } from '../mock-data/imaging-catalog.mock';
import { IMAGING_ORDERS } from '../mock-data/imaging-orders.mock';
import { generateSlots } from '../mock-data/schedule-slots.mock';
import type {
  ID,
  ImagingExam,
  ImagingModality,
  ImagingOrder,
  ImagingOrderDraft,
  ISODate,
  OrderStatus,
  OrderUrgency,
  ScheduleSlot,
} from '../models';
import { mockError, mockResponse, nextId } from '../utils/mock-response';

@Injectable({ providedIn: 'root' })
export class ImagingOrderService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly catalog: ImagingExam[] = structuredClone(IMAGING_CATALOG);
  private readonly orders: ImagingOrder[] = structuredClone(IMAGING_ORDERS);
  private sequence = this.orders.length;

  getCatalog(modality?: ImagingModality): Observable<ImagingExam[]> {
    const result = modality ? this.catalog.filter((e) => e.modality === modality) : this.catalog;
    return mockResponse(result, this.latency);
  }

  getSlots(modality: ImagingModality, date: ISODate): Observable<ScheduleSlot[]> {
    return mockResponse(generateSlots(modality, date), this.latency);
  }

  getOrders(filter?: {
    patientId?: ID;
    status?: OrderStatus;
    urgency?: OrderUrgency;
  }): Observable<ImagingOrder[]> {
    let result = this.orders;
    if (filter?.patientId) result = result.filter((o) => o.patientId === filter.patientId);
    if (filter?.status) result = result.filter((o) => o.status === filter.status);
    if (filter?.urgency) result = result.filter((o) => o.urgency === filter.urgency);
    return mockResponse(result, this.latency);
  }

  getOrderById(id: ID): Observable<ImagingOrder> {
    const found = this.orders.find((o) => o.id === id);
    if (!found) return mockError(`Nie znaleziono zlecenia o id ${id}`, this.latency);
    return mockResponse(found, this.latency);
  }

  createOrder(draft: ImagingOrderDraft): Observable<ImagingOrder> {
    this.sequence++;
    const now = new Date().toISOString();
    const status: OrderStatus = draft.slotId ? 'scheduled' : 'ordered';
    const order: ImagingOrder = {
      ...draft,
      id: nextId('iord', this.sequence),
      orderedAt: now,
      status,
      statusHistory: [{ status, at: now, byId: draft.orderedById }],
    };
    this.orders.push(order);
    return mockResponse(order, this.latency);
  }

  updateStatus(id: ID, status: OrderStatus, note?: string): Observable<ImagingOrder> {
    const index = this.orders.findIndex((o) => o.id === id);
    if (index === -1) return mockError(`Nie znaleziono zlecenia o id ${id}`, this.latency);
    const now = new Date().toISOString();
    const updated: ImagingOrder = {
      ...this.orders[index],
      status,
      statusHistory: [...this.orders[index].statusHistory, { status, at: now, note }],
    };
    this.orders[index] = updated;
    return mockResponse(updated, this.latency);
  }

  cancelOrder(id: ID, reason: string): Observable<ImagingOrder> {
    return this.updateStatus(id, 'cancelled', reason);
  }
}
