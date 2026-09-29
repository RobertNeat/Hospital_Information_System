import type { ID, ISODate, ISODateTime } from './common.model';
import type { AdministrationRoute, DrugForm, ReimbursementLevel } from './drug.model';

/**
 * Labels: 1x dziennie, 2x dziennie, 3x dziennie, 4x dziennie,
 * co 4 h, co 6 h, co 8 h, co 12 h, 1x w tygodniu, doraźnie.
 */
export type DoseFrequency =
  'QD' | 'BID' | 'TID' | 'QID' | 'Q4H' | 'Q6H' | 'Q8H' | 'Q12H' | 'QW' | 'PRN';

export interface DosageInstruction {
  dose: number;
  doseUnit: string;
  route: AdministrationRoute;
  frequency: DoseFrequency;
  timesOfDay?: ('morning' | 'noon' | 'evening' | 'night')[];
  durationDays: number;
  asNeeded: boolean;
  maxPerDay?: number;
  instructions?: string;
}

export interface PrescriptionItem {
  drugId: ID;
  drugName: string;
  activeSubstance: string;
  strength: string;
  form: DrugForm;
  dosage: DosageInstruction;
  quantityPackages: number;
  reimbursement: ReimbursementLevel;
  substitutionAllowed: boolean;
}

export type PrescriptionStatus =
  'issued' | 'partially_dispensed' | 'dispensed' | 'cancelled' | 'expired';

export interface Prescription {
  id: ID;
  patientId: ID;
  prescriberId: ID;
  issuedAt: ISODateTime;
  validFrom: ISODate;
  validUntil: ISODate;
  kind: 'e_prescription' | 'hospital_order';
  items: PrescriptionItem[];
  status: PrescriptionStatus;
  /** 4 digits. */
  accessCode: string;
  /** Mock 44-character key. */
  eRxKey: string;
  notes?: string;
}

export type PrescriptionDraft = Omit<
  Prescription,
  'id' | 'issuedAt' | 'status' | 'accessCode' | 'eRxKey'
>;

export interface DrugSafetyWarning {
  type: 'allergy' | 'interaction' | 'duplicate' | 'max_dose';
  severity: 'warn' | 'danger';
  message: string;
}
