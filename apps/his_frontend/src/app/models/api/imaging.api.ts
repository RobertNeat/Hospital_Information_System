import type { ID, ISODate } from '../common.model';
import type { ImagingModality, ImagingOrder } from '../imaging.model';
import type { OrderStatus, OrderUrgency, ResultAbnormalityFilter } from '../lab.model';
import type { PageQuery } from './common.api';

/** `orderedById` is omitted: the actor always comes from the session (anti-spoofing). */
export type ImagingOrderCreateRequest = Omit<
  ImagingOrder,
  | 'id'
  | 'orderedById'
  | 'orderedAt'
  | 'status'
  | 'statusHistory'
  | 'version'
  | 'createdAt'
  | 'createdById'
  | 'updatedAt'
  | 'updatedById'
>;

export interface ImagingOrderQuery extends PageQuery {
  patientId?: ID;
  status?: OrderStatus;
  urgency?: OrderUrgency;
  modality?: ImagingModality;
}

/** Imaging order list filter without paging (services return `T[]`, not `Page<T>`). */
export type ImagingOrderFilter = Omit<ImagingOrderQuery, keyof PageQuery>;

export interface ImagingResultQuery extends PageQuery {
  patientId?: ID;
  filter?: ResultAbnormalityFilter;
}

export interface SlotQuery {
  modality: ImagingModality;
  date: ISODate;
}
