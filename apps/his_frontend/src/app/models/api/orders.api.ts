import type { OrderStatus } from '../lab.model';

/** Shared by lab and imaging orders; the actor comes from the session. */
export interface OrderStatusUpdateRequest {
  status: OrderStatus;
  note?: string;
  version?: number;
}

export interface OrderCancelRequest {
  reason: string;
  version?: number;
}

/** Shared by lab and imaging results; the actor comes from the session. */
export interface ResultAcknowledgeRequest {
  version?: number;
}
