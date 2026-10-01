import type { Auditable, ID, ISODate, ISODateTime, Versioned } from './common.model';
import type { AdministrationRoute, DrugForm, ReimbursementLevel } from './drug.model';

/**
 * Labels: 1x dziennie, 2x dziennie, 3x dziennie, 4x dziennie,
 * co 4 h, co 6 h, co 8 h, co 12 h, 1x w tygodniu, doraźnie.
 */
export type DoseFrequency =
  'QD' | 'BID' | 'TID' | 'QID' | 'Q4H' | 'Q6H' | 'Q8H' | 'Q12H' | 'QW' | 'PRN';

export type TimeOfDay = 'morning' | 'noon' | 'evening' | 'night';

export type PrescriptionKind = 'e_prescription' | 'hospital_order';

export interface DosageInstruction {
  dose: number;
  doseUnit: string;
  route: AdministrationRoute;
  frequency: DoseFrequency;
  timesOfDay?: TimeOfDay[];
  durationDays: number;
  asNeeded: boolean;
  maxPerDay?: number;
  instructions?: string;
}

export interface PrescriptionItem {
  id?: ID;
  drugId: ID;
  /**
   * @snapshot Copied from the drug catalog at the time of issuing. Frozen
   * intentionally: later catalog changes must not alter an issued prescription.
   */
  drugName: string;
  /** @snapshot See `drugName`. */
  activeSubstance: string;
  /** @snapshot See `drugName`. */
  strength: string;
  /** @snapshot See `drugName`. */
  form: DrugForm;
  dosage: DosageInstruction;
  quantityPackages: number;
  reimbursement: ReimbursementLevel;
  substitutionAllowed: boolean;
}

/**
 * @projection Item of an active prescription, tagged with its origin prescription.
 */
export interface ActiveMedication extends PrescriptionItem {
  prescriptionId: ID;
  /** Prescription start date (`validFrom`). */
  date: ISODate;
}

export type PrescriptionStatus =
  'issued' | 'partially_dispensed' | 'dispensed' | 'cancelled' | 'expired';

export interface Prescription extends Versioned, Partial<Auditable> {
  id: ID;
  patientId: ID;
  encounterId?: ID;
  prescriberId: ID;
  issuedAt: ISODateTime;
  validFrom: ISODate;
  validUntil: ISODate;
  kind: PrescriptionKind;
  items: PrescriptionItem[];
  /** Effective status: `expired` is derived by the backend from `validUntil` on read. */
  status: PrescriptionStatus;
  /** 4 digits. Assigned by the backend; the client does not send it. */
  accessCode: string;
  /**
   * 44-character key assigned by the backend; the client does not send it. For `e_prescription`
   * the e-receipt key may replace the initial local one after issuing, so re-read the
   * prescription instead of treating the key from the issue response as final.
   */
  eRxKey?: string;
  notes?: string;
  cancelledAt?: ISODateTime;
  cancelReason?: string;
}

/** @deprecated Use `PrescriptionCreateRequest` from `models/api`. */
export type PrescriptionDraft = Omit<
  Prescription,
  | 'id'
  | 'issuedAt'
  | 'status'
  | 'accessCode'
  | 'eRxKey'
  | 'version'
  | 'createdAt'
  | 'createdById'
  | 'updatedAt'
  | 'updatedById'
  | 'cancelledAt'
  | 'cancelReason'
>;

export type DrugSafetyWarningType = 'allergy' | 'interaction' | 'duplicate' | 'max_dose';

export type DrugSafetySeverity = 'warn' | 'danger';

/** @projection Backend response of the drug safety check. */
export interface DrugSafetyWarning {
  type: DrugSafetyWarningType;
  severity: DrugSafetySeverity;
  drugId?: ID;
  message: string;
}
