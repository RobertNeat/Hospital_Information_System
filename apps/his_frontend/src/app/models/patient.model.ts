import type { Address, Gender, ID, ISODate, ISODateTime } from './common.model';

export type AdmissionStatus = 'registered' | 'admitted' | 'outpatient' | 'discharged';

export type TriageLevel = 'red' | 'orange' | 'yellow' | 'green' | 'blue';

export type PatientFlag = 'isolation' | 'fall_risk' | 'dnr' | 'infection_risk' | 'vip';

export type BloodType = 'A+' | 'A-' | 'B+' | 'B-' | 'AB+' | 'AB-' | '0+' | '0-';

export interface IdentityDocument {
  type: 'id_card' | 'passport' | 'other';
  number: string;
}

export interface EmergencyContact {
  fullName: string;
  relation: string;
  phone: string;
  isLegalGuardian: boolean;
}

export interface Insurance {
  status: 'active' | 'inactive' | 'unknown';
  nfzBranch: string;
  payer: 'NFZ' | 'private' | 'none';
  ewusVerifiedAt?: ISODateTime;
}

export interface Admission {
  admissionType: 'planned' | 'emergency' | 'transfer' | 'outpatient';
  admittedAt: ISODateTime;
  wardId: ID;
  room?: string;
  bed?: string;
  attendingPhysicianId: ID;
  triageLevel?: TriageLevel;
  reason: string;
  referralNumber?: string;
  dischargedAt?: ISODateTime;
}

export interface Patient {
  id: ID;
  /** Case-history number, e.g. 'HIS/2026/000123'. */
  mrn: string;
  pesel: string | null;
  noPeselReason?: 'foreigner' | 'newborn' | 'unknown_identity';
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
  status: AdmissionStatus;
  currentAdmission?: Admission;
  flags: PatientFlag[];
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export type PatientSummary = Pick<
  Patient,
  'id' | 'mrn' | 'pesel' | 'firstName' | 'lastName' | 'birthDate' | 'gender' | 'status' | 'flags'
> & {
  wardName?: string;
  bed?: string;
};

export type PatientDraft = Omit<Patient, 'id' | 'mrn' | 'createdAt' | 'updatedAt'>;

export interface PatientSearchQuery {
  term?: string;
  status?: AdmissionStatus;
  wardId?: ID;
}
