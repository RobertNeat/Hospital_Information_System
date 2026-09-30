import type { ID } from '../common.model';
import type { DosageInstruction, PrescriptionDraft } from '../prescription.model';
import type { PageQuery } from './common.api';

export type PrescriptionCreateRequest = PrescriptionDraft;

export interface PrescriptionQuery extends PageQuery {
  patientId?: ID;
  prescriberId?: ID;
}

/** Prescription list filter without paging (services return `T[]`, not `Page<T>`). */
export type PrescriptionFilter = Omit<PrescriptionQuery, keyof PageQuery>;

export interface PrescriptionCancelRequest {
  reason?: string;
  version?: number;
}

export interface DrugSafetyCheckRequest {
  patientId: ID;
  drugId: ID;
  dosage?: DosageInstruction;
}

export interface DrugSearchQuery {
  term: string;
}
