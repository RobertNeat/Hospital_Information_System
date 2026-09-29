import type { Coding, ID, ISODateTime } from './common.model';

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

export type OrderStatus =
  'ordered' | 'scheduled' | 'specimen_collected' | 'in_progress' | 'completed' | 'cancelled';

export interface LabTest {
  code: string;
  loinc?: string;
  name: string;
  category: LabCategory;
  specimenTypes: SpecimenType[];
  defaultSpecimen: SpecimenType;
  turnaroundHours: number;
  fastingRequired: boolean;
  analytes: { code: string; name: string; unit: string; low?: number; high?: number }[];
}

export interface LabOrderItem {
  testCode: string;
  testName: string;
  specimenType: SpecimenType;
}

export interface StatusChange {
  status: OrderStatus;
  at: ISODateTime;
  byId?: ID;
  note?: string;
}

export interface LabOrder {
  id: ID;
  patientId: ID;
  orderedById: ID;
  orderedAt: ISODateTime;
  items: LabOrderItem[];
  urgency: OrderUrgency;
  fasting: boolean;
  plannedCollectionAt: ISODateTime;
  diagnosisCode?: Coding;
  clinicalInfo: string;
  notes?: string;
  status: OrderStatus;
  statusHistory: StatusChange[];
}

export type LabOrderDraft = Omit<LabOrder, 'id' | 'orderedAt' | 'status' | 'statusHistory'>;

export type ResultFlag = 'N' | 'L' | 'H' | 'LL' | 'HH' | 'A';

export interface LabObservation {
  analyteCode: string;
  analyteName: string;
  value: number | string;
  unit: string;
  referenceRange: { low?: number; high?: number; text?: string };
  flag: ResultFlag;
}

export interface LabResult {
  id: ID;
  patientId: ID;
  orderId?: ID;
  testCode: string;
  testName: string;
  category: LabCategory;
  collectedAt: ISODateTime;
  resultedAt: ISODateTime;
  status: 'preliminary' | 'final' | 'corrected';
  observations: LabObservation[];
  performerName: string;
  comment?: string;
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
