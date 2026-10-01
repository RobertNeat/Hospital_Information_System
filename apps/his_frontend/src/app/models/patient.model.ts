import type {
  Address,
  Auditable,
  Gender,
  ID,
  ISODate,
  ISODateTime,
  Versioned,
} from './common.model';

export type { PatientSearchQuery } from './api/patient.api';

export type AdmissionStatus = 'registered' | 'admitted' | 'outpatient' | 'discharged';

export type TriageLevel = 'red' | 'orange' | 'yellow' | 'green' | 'blue';

export type PatientFlag = 'isolation' | 'fall_risk' | 'dnr' | 'infection_risk' | 'vip';

export type BloodType = 'A+' | 'A-' | 'B+' | 'B-' | 'AB+' | 'AB-' | '0+' | '0-';

export type AdmissionType = 'planned' | 'emergency' | 'transfer' | 'outpatient';

export type NoPeselReason = 'foreigner' | 'newborn' | 'unknown_identity';

export type IdentityDocumentType = 'id_card' | 'passport' | 'other';

export type InsuranceStatus = 'active' | 'inactive' | 'unknown';

export type InsurancePayer = 'NFZ' | 'private' | 'none';

export interface IdentityDocument {
  type: IdentityDocumentType;
  number: string;
}

export interface EmergencyContact {
  fullName: string;
  relation: string;
  phone: string;
  isLegalGuardian: boolean;
}

export interface Insurance {
  status: InsuranceStatus;
  nfzBranch: string;
  payer: InsurancePayer;
  ewusVerifiedAt?: ISODateTime;
}

/** Lifecycle of a single Admission record (distinct from the derived `Patient.status`). */
export type AdmissionRecordStatus = 'active' | 'discharged' | 'cancelled';

export type DischargeDisposition = 'home' | 'transfer' | 'deceased' | 'against_advice' | 'other';

/**
 * Administrative ADT process of a hospital stay. A patient can have many admissions (history),
 * at most one of them `active`. Target model: 1:1 with an Encounter of type 'hospitalization'
 * (Admission owns the link via `encounterId`; Encounter is the clinical context).
 * Fields marked as server-assigned are set by the backend and absent in `AdmitPatientRequest`.
 */
export interface Admission extends Versioned {
  /** Server-assigned. */
  id?: ID;
  /** Server-assigned. */
  patientId?: ID;
  /** Server-assigned link to the hospitalization Encounter. */
  encounterId?: ID;
  /** Server-assigned. */
  status?: AdmissionRecordStatus;
  admissionType: AdmissionType;
  admittedAt: ISODateTime;
  wardId: ID;
  room?: string;
  bed?: string;
  attendingPhysicianId: ID;
  triageLevel?: TriageLevel;
  reason: string;
  referralNumber?: string;
  dischargedAt?: ISODateTime;
  dischargeDisposition?: DischargeDisposition;
  dischargeSummaryNoteId?: ID;
}

export interface Patient
  extends Partial<Pick<Auditable, 'createdById' | 'updatedById'>>, Versioned {
  id: ID;
  /** Case-history number, e.g. 'HIS/2026/000123'. */
  mrn: string;
  pesel: string | null;
  noPeselReason?: NoPeselReason;
  identityDocument?: IdentityDocument;
  firstName: string;
  secondName?: string;
  lastName: string;
  birthDate: ISODate;
  gender: Gender;
  phone?: string;
  email?: string;
  address: Address;
  emergencyContact?: EmergencyContact;
  insurance: Insurance;
  bloodType?: BloodType;
  /** Derived from the admission state (stored, kept in sync by the backend). */
  status: AdmissionStatus;
  /**
   * @projection Projection of the active Admission (status 'active'); at most one active
   * admission per patient. The full history is available via `getAdmissions`.
   */
  currentAdmission?: Admission;
  flags: PatientFlag[];
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export type PatientSummary = Pick<
  Patient,
  'id' | 'mrn' | 'pesel' | 'firstName' | 'lastName' | 'birthDate' | 'gender' | 'status' | 'flags'
> & {
  /** @projection Ward name resolved from the active admission; only for `admitted` patients. */
  wardName?: string;
  /** @projection Bed from the active admission; only for `admitted` patients. */
  bed?: string;
};

/**
 * Payload for registering a patient; `PatientCreateRequest` (models/api) is an alias.
 * Server-assigned fields (`id`, `mrn`, `status`, `currentAdmission`), audit fields and `version` are never sent by the client.
 */
export type PatientDraft = Omit<
  Patient,
  | 'id'
  | 'mrn'
  | 'status'
  | 'currentAdmission'
  | 'createdAt'
  | 'updatedAt'
  | 'createdById'
  | 'updatedById'
  | 'version'
>;
