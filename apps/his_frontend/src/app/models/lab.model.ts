import type { Auditable, Coding, ID, ISODateTime, Versioned } from './common.model';

/** Labels: Rutynowe, Pilne, Natychmiastowe (CITO). */
export type OrderUrgency = 'routine' | 'urgent' | 'stat';

/** Labels: Krew pełna, Surowica, Mocz, Kał, Wymaz, Płyn mózgowo-rdzeniowy, Wycinek tkankowy. */
export type SpecimenType = 'blood' | 'serum' | 'urine' | 'stool' | 'swab' | 'csf' | 'tissue';

/** `pathology` covers biopsy and histopathology. */
export type LabCategory =
  | 'hematology'
  | 'biochemistry'
  | 'coagulation'
  | 'immunology'
  | 'urinalysis'
  | 'microbiology'
  | 'pathology';

/** Shared by lab and imaging orders. `specimen_collected` applies to laboratory orders only. */
export type OrderStatus =
  'ordered' | 'scheduled' | 'specimen_collected' | 'in_progress' | 'completed' | 'cancelled';

export interface LabAnalyteDefinition {
  code: string;
  name: string;
  unit: string;
  low?: number;
  high?: number;
}

export interface LabTest {
  code: string;
  loinc?: string;
  name: string;
  category: LabCategory;
  specimenTypes: SpecimenType[];
  defaultSpecimen: SpecimenType;
  turnaroundHours: number;
  fastingRequired: boolean;
  analytes: LabAnalyteDefinition[];
}

export interface LabPanel {
  id: string;
  name: string;
  testCodes: string[];
}

export interface LabOrderItem {
  /** Out of scope: item identifier (no per-item persistence yet). */
  id?: ID;
  testCode: string;
  /** @snapshot Test name at the time of ordering. */
  testName: string;
  /** Out of scope: the specimen entity is not modelled yet. */
  specimenId?: ID;
  specimenType: SpecimenType;
}

export interface StatusChange {
  status: OrderStatus;
  at: ISODateTime;
  byId?: ID;
  note?: string;
}

export interface LabOrder extends Versioned, Partial<Auditable> {
  id: ID;
  patientId: ID;
  encounterId?: ID;
  orderedById: ID;
  orderedAt: ISODateTime;
  items: LabOrderItem[];
  urgency: OrderUrgency;
  fasting: boolean;
  plannedCollectionAt: ISODateTime;
  diagnosisCode?: Coding;
  clinicalInfo: string;
  notes?: string;
  /** Changed only through actions (updateStatus / cancel); the actor comes from the session. */
  status: OrderStatus;
  statusHistory: StatusChange[];
}

export type ResultFlag = 'N' | 'L' | 'H' | 'LL' | 'HH' | 'A';

export interface ReferenceRange {
  low?: number;
  high?: number;
  text?: string;
}

export interface LabObservation {
  analyteCode: string;
  /** @snapshot Analyte name at the time of resulting. */
  analyteName: string;
  value: number | string;
  unit: string;
  referenceRange: ReferenceRange;
  flag: ResultFlag;
}

export type LabResultStatus = 'preliminary' | 'final' | 'corrected';

export type ResultAbnormalityFilter = 'all' | 'abnormal' | 'critical';

export interface ResultReview {
  reviewedAt: ISODateTime;
  reviewedById: ID;
}

export interface LabResult {
  id: ID;
  patientId: ID;
  /** Required for internal orders, optional for external ones. */
  orderId?: ID;
  orderItemId?: ID;
  testCode: string;
  /** @snapshot Test name at the time of resulting. */
  testName: string;
  category: LabCategory;
  collectedAt: ISODateTime;
  resultedAt: ISODateTime;
  status: LabResultStatus;
  observations: LabObservation[];
  /** @snapshot Performer name at the time of resulting. */
  performerName: string;
  comment?: string;
  /** Set when the result is acknowledged (see `ResultReview`). */
  reviewedAt?: ISODateTime;
  reviewedById?: ID;
}

export interface TrendPoint {
  at: ISODateTime;
  value: number;
  flag?: ResultFlag;
}

export interface AnalyteTrend {
  analyteCode: string;
  analyteName: string;
  unit: string;
  low?: number;
  high?: number;
  points: TrendPoint[];
}
