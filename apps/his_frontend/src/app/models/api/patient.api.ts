import type { ID, ISODateTime, Versioned } from '../common.model';
import type {
  Admission,
  AdmissionRecordStatus,
  AdmissionStatus,
  DischargeDisposition,
  PatientDraft,
  PatientSummary,
} from '../patient.model';

export type PatientCreateRequest = PatientDraft;

/**
 * PATCH body: an absent field is left unchanged, `null` clears it, nested objects are replaced.
 * `version` is the patient version for optimistic locking (409 on mismatch).
 */
export type PatientUpdateRequest = {
  [K in keyof PatientDraft]?: PatientDraft[K] | null;
} & Versioned;

/** Filters of `GET /patients`; the service handles paging. */
export interface PatientSearchQuery {
  term?: string;
  status?: AdmissionStatus;
  wardId?: ID;
}

/** Admission data without backend-assigned fields. */
export type AdmitPatientRequest = Omit<
  Admission,
  | 'id'
  | 'patientId'
  | 'encounterId'
  | 'status'
  | 'dischargedAt'
  | 'dischargeDisposition'
  | 'dischargeSummaryNoteId'
  | 'version'
>;

export interface DischargePatientRequest {
  dischargedAt: ISODateTime;
  disposition?: DischargeDisposition;
  summaryNoteId?: ID;
  version?: number;
}

/**
 * Optional discharge details (client-side call shape; subset of `DischargePatientRequest`).
 * `version` is the version of the active admission (`currentAdmission.version`), not the patient's.
 */
export type DischargeOptions = Pick<
  DischargePatientRequest,
  'disposition' | 'summaryNoteId' | 'version'
>;

export interface AdmissionQuery {
  patientId?: ID;
  status?: AdmissionRecordStatus;
}

/** Duplicate check by PESEL: the matching patient or `null` when none exists. */
export type PatientDuplicateCheckResponse = PatientSummary | null;
