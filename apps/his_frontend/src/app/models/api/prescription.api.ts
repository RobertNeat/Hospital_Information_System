import type { ID } from '../common.model';
import type {
  DosageInstruction,
  PrescriptionDraft,
  PrescriptionKind,
  PrescriptionStatus,
} from '../prescription.model';
import type { PageQuery } from './common.api';

export type PrescriptionCreateRequest = PrescriptionDraft;

export interface PrescriptionQuery extends PageQuery {
  patientId?: ID;
  prescriberId?: ID;
  /** Effective status (`expired` includes live prescriptions past `validUntil`). */
  status?: PrescriptionStatus;
  kind?: PrescriptionKind;
}

/** Prescription list filter without paging (services return `T[]`, not `Page<T>`). */
export type PrescriptionFilter = Omit<PrescriptionQuery, keyof PageQuery>;

export interface PrescriptionCancelRequest {
  reason?: string;
  version?: number;
}

/** One position of the working prescription. */
export interface DrugSafetyItemRequest {
  drugId: ID;
  dosage?: DosageInstruction;
}

/** `drugId` and/or non-empty `items` are required; both together form one list. */
export interface DrugSafetyCheckRequest {
  patientId: ID;
  drugId?: ID;
  dosage?: DosageInstruction;
  items?: DrugSafetyItemRequest[];
}

export interface DrugSearchQuery {
  term: string;
}
