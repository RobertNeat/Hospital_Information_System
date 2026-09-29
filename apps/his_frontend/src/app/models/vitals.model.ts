import type { ID, ISODateTime } from './common.model';
import type { PatientSummary } from './patient.model';

export type VitalType =
  'systolic' | 'diastolic' | 'heartRate' | 'temperature' | 'spo2' | 'respiratoryRate';

export interface VitalSigns {
  id: ID;
  patientId: ID;
  recordedAt: ISODateTime;
  recordedById: ID;
  context: 'office_exam' | 'ward_round' | 'triage' | 'observation';
  systolic?: number;
  diastolic?: number;
  heartRate?: number;
  temperature?: number;
  spo2?: number;
  respiratoryRate?: number;
  painScore?: number;
  notes?: string;
}

export type VitalSignsDraft = Omit<VitalSigns, 'id'>;

export interface VitalThreshold {
  type: VitalType;
  label: string;
  unit: string;
  low: number;
  high: number;
  criticalLow: number;
  criticalHigh: number;
  /** Physically plausible input bounds. */
  min: number;
  max: number;
}

export interface VitalAnomaly {
  type: VitalType;
  value: number;
  severity: 'warning' | 'critical';
  direction: 'low' | 'high';
  message: string;
  recordedAt: ISODateTime;
}

export interface WardVitalsRow {
  patient: PatientSummary;
  latest?: VitalSigns;
  anomalies: VitalAnomaly[];
  lastMeasuredAgoMin?: number;
}
