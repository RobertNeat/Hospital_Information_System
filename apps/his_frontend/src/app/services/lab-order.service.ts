import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import {
  LAB_ORDERS_URL,
  LAB_PANELS_URL,
  LAB_TESTS_URL,
  labOrderCancelUrl,
  labOrderStatusUrl,
  labOrderUrl,
  patientLabOrdersUrl,
} from '../config/api.config';
import type { ID, LabOrder, LabPanel, LabTest, OrderStatus } from '../models';
import type {
  LabOrderCreateRequest,
  LabOrderFilter,
  Page,
  OrderCancelRequest,
  OrderStatusUpdateRequest,
} from '../models/api';
import { toHttpParams } from '../utils/http-params';
import { MAX_PAGE_SIZE, readAllPages } from '../utils/read-all-pages';

/**
 * Lab catalog and orders backed by `/lab-tests`, `/lab-panels` and `/lab-orders`. The backend owns
 * ids, the state machine and the status history. Failures (404/409/422) arrive as
 * `HttpErrorResponse` with a `ProblemDetail` body (see `toApiError`); 422 `errors[].field` is e.g.
 * `items[0].testCode` (`notFound`/`duplicate`), `fasting` (`fastingRequired`).
 */
@Injectable({ providedIn: 'root' })
export class LabOrderService {
  private readonly http = inject(HttpClient);

  getCatalog(): Observable<LabTest[]> {
    return this.http.get<LabTest[]>(LAB_TESTS_URL);
  }

  getPanels(): Observable<LabPanel[]> {
    return this.http.get<LabPanel[]>(LAB_PANELS_URL);
  }

  /** All orders matching the filter, newest first (every page is read). */
  getOrders(filter?: LabOrderFilter): Observable<LabOrder[]> {
    return readAllPages((page) =>
      this.http.get<Page<LabOrder>>(LAB_ORDERS_URL, {
        params: toHttpParams({
          patientId: filter?.patientId,
          status: filter?.status,
          urgency: filter?.urgency,
          orderedFrom: filter?.orderedFrom,
          orderedTo: filter?.orderedTo,
          page,
          size: MAX_PAGE_SIZE,
        }),
      }),
    );
  }

  getOrderById(id: ID): Observable<LabOrder> {
    return this.http.get<LabOrder>(labOrderUrl(id));
  }

  /** The orderer comes from the token (`orderedById` is ignored); `testName` is a catalog snapshot. */
  createOrder(draft: LabOrderCreateRequest): Observable<LabOrder> {
    return this.http.post<LabOrder>(patientLabOrdersUrl(draft.patientId), draft);
  }

  /** Pass the loaded `version`; 409 on a forbidden transition or version mismatch. Not for `cancelled`. */
  updateStatus(id: ID, status: OrderStatus, note?: string, version?: number): Observable<LabOrder> {
    const body: OrderStatusUpdateRequest = { status, note, version };
    return this.http.post<LabOrder>(labOrderStatusUrl(id), body);
  }

  cancelOrder(id: ID, reason: string, version?: number): Observable<LabOrder> {
    const body: OrderCancelRequest = { reason, version };
    return this.http.post<LabOrder>(labOrderCancelUrl(id), body);
  }
}
