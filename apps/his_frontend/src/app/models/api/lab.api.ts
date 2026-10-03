import type { ID } from '../common.model';
import type { LabOrder, OrderStatus, OrderUrgency, ResultAbnormalityFilter } from '../lab.model';
import type { PatientSummary } from '../patient.model';
import type { PageQuery } from './common.api';

/** `GET /patients/{id}/lab-results/analytes` item (the client maps it to `SelectOption`). */
export interface LabAnalyteRef {
  code: string;
  name: string;
}

/** `orderedById` is omitted: the actor always comes from the session (anti-spoofing). */
export type LabOrderCreateRequest = Omit<
  LabOrder,
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

export interface LabOrderQuery extends PageQuery {
  patientId?: ID;
  status?: OrderStatus;
  urgency?: OrderUrgency;
  /** Inclusive bounds on `orderedAt` (ISO-8601; a `Date` is sent as UTC). */
  orderedFrom?: string | Date;
  orderedTo?: string | Date;
}

/** Lab order list filter without paging (services return `T[]`, not `Page<T>`). */
export type LabOrderFilter = Omit<LabOrderQuery, keyof PageQuery>;

export interface LabResultQuery extends PageQuery {
  patientId?: ID;
  filter?: ResultAbnormalityFilter;
}

/**
 * @projection Result enriched with a patient summary for inbox-style views. `patient` can be
 * absent when the backend finds no summary for `patientId`; consumers fall back to the id.
 */
export type ResultWithPatient<T> = T & { patient?: PatientSummary };
