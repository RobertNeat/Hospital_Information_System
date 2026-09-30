import type { ID } from '../common.model';
import type {
  LabOrderDraft,
  OrderStatus,
  OrderUrgency,
  ResultAbnormalityFilter,
} from '../lab.model';
import type { PatientSummary } from '../patient.model';
import type { PageQuery } from './common.api';

export type LabOrderCreateRequest = LabOrderDraft;

export interface LabOrderQuery extends PageQuery {
  patientId?: ID;
  status?: OrderStatus;
  urgency?: OrderUrgency;
}

/** Lab order list filter without paging (services return `T[]`, not `Page<T>`). */
export type LabOrderFilter = Omit<LabOrderQuery, keyof PageQuery>;

export interface LabResultQuery extends PageQuery {
  patientId?: ID;
  filter?: ResultAbnormalityFilter;
}

/** @projection Result enriched with a patient summary for inbox-style views. */
export type ResultWithPatient<T> = T & { patient: PatientSummary };
