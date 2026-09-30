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

export type PatientUpdateRequest = Partial<PatientDraft> & Versioned;

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

/** Optional discharge details (client-side call shape; subset of `DischargePatientRequest`). */
export type DischargeOptions = Pick<DischargePatientRequest, 'disposition' | 'summaryNoteId'>;

export interface AdmissionQuery {
  patientId?: ID;
  status?: AdmissionRecordStatus;
}

/** Duplicate check by PESEL: the matching patient or `null` when none exists. */
export type PatientDuplicateCheckResponse = PatientSummary | null;
