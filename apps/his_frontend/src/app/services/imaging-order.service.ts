import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import {
  IMAGING_EXAMS_URL,
  IMAGING_ORDERS_URL,
  IMAGING_SLOTS_URL,
  imagingOrderCancelUrl,
  imagingOrderStatusUrl,
  imagingOrderUrl,
  patientImagingOrdersUrl,
} from '../config/api.config';
import type {
  ID,
  ImagingExam,
  ImagingModality,
  ImagingOrder,
  ISODate,
  OrderStatus,
  ScheduleSlot,
} from '../models';
import type {
  ImagingOrderCreateRequest,
  ImagingOrderFilter,
  OrderCancelRequest,
  OrderStatusUpdateRequest,
  Page,
} from '../models/api';
import { toHttpParams } from '../utils/http-params';
import { MAX_PAGE_SIZE, readAllPages } from '../utils/read-all-pages';

/**
 * Imaging catalog, slots and orders backed by `/imaging-exams`, `/imaging-slots` and
 * `/imaging-orders`. The backend owns ids, the state machine (no `specimen_collected`), slot
 * reservation and the status history. Failures (404/409/422) arrive as `HttpErrorResponse` with a
 * `ProblemDetail` body (see `toApiError`); 422 `errors[].field` is e.g. `examCode`/`slotId`
 * (`notFound`), `laterality` (`required`), `contrast` (`notAllowed`), `safety.confirmed`
 * (`required`); 409 on a taken slot.
 */
@Injectable({ providedIn: 'root' })
export class ImagingOrderService {
  private readonly http = inject(HttpClient);

  getCatalog(modality?: ImagingModality): Observable<ImagingExam[]> {
    return this.http.get<ImagingExam[]>(IMAGING_EXAMS_URL, { params: toHttpParams({ modality }) });
  }

  /** All slots of the modality starting on `date` (Europe/Warsaw day), taken ones have `available=false`. */
  getSlots(modality: ImagingModality, date: ISODate): Observable<ScheduleSlot[]> {
    return this.http.get<ScheduleSlot[]>(IMAGING_SLOTS_URL, {
      params: toHttpParams({ modality, date }),
    });
  }

  /** All orders matching the filter, newest first (every page is read). */
  getOrders(filter?: ImagingOrderFilter): Observable<ImagingOrder[]> {
    return readAllPages((page) =>
      this.http.get<Page<ImagingOrder>>(IMAGING_ORDERS_URL, {
        params: toHttpParams({
          patientId: filter?.patientId,
          status: filter?.status,
          urgency: filter?.urgency,
          modality: filter?.modality,
          page,
          size: MAX_PAGE_SIZE,
        }),
      }),
    );
  }

  getOrderById(id: ID): Observable<ImagingOrder> {
    return this.http.get<ImagingOrder>(imagingOrderUrl(id));
  }

  /**
   * The orderer always comes from the token (the request carries no actor field); exam name/modality/body
   * region are taken from the catalog. With `slotId` the order is `scheduled` at once and the slot is reserved.
   */
  createOrder(draft: ImagingOrderCreateRequest): Observable<ImagingOrder> {
    return this.http.post<ImagingOrder>(patientImagingOrdersUrl(draft.patientId), draft);
  }

  /** Pass the loaded `version`; 409 on a forbidden transition or version mismatch. Not for `cancelled`. */
  updateStatus(
    id: ID,
    status: OrderStatus,
    note?: string,
    version?: number,
  ): Observable<ImagingOrder> {
    const body: OrderStatusUpdateRequest = { status, note, version };
    return this.http.post<ImagingOrder>(imagingOrderStatusUrl(id), body);
  }

  /** Releases a reserved slot; `reason` is required. */
  cancelOrder(id: ID, reason: string, version?: number): Observable<ImagingOrder> {
    const body: OrderCancelRequest = { reason, version };
    return this.http.post<ImagingOrder>(imagingOrderCancelUrl(id), body);
  }
}
