import type { ID, ISODateTime } from './common.model';
import type { PatientSummary } from './patient.model';

export type VitalType =
  'systolic' | 'diastolic' | 'heartRate' | 'temperature' | 'spo2' | 'respiratoryRate';

/** Circumstances in which the measurement was taken. */
export type VitalsContext = 'office_exam' | 'ward_round' | 'triage' | 'observation';

/** How the reading got into the system. */
export type VitalsSource = 'manual' | 'monitor';

/** Time window for `GET /patients/{id}/vitals`. */
export type VitalsRange = '24h' | '7d' | '30d' | 'all';

export type AnomalySeverity = 'warning' | 'critical';

export type AnomalyDirection = 'low' | 'high';

/**
 * Single vital-signs measurement (immutable: no update/version; correction = new reading).
 * A flat row with nullable columns is a deliberate decision (no EAV / observation list).
 * `painScore` is outside `VitalType` and has no thresholds.
 * `recordedById` is the actor from the session (assigned by the server).
 */
export interface VitalSigns {
  id: ID;
  patientId: ID;
  recordedAt: ISODateTime;
  recordedById: ID;
  context: VitalsContext;
  /** Origin of the reading; absent = `manual`. */
  source?: VitalsSource;
  /** Monitoring device identifier (when `source === 'monitor'`). */
  deviceId?: string;
  encounterId?: ID;
  systolic?: number;
  diastolic?: number;
  heartRate?: number;
  temperature?: number;
  spo2?: number;
  respiratoryRate?: number;
  painScore?: number;
  notes?: string;
}

/** Configuration served by `GET /vital-thresholds` (read-only for the client). */
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

/** @projection Computed by the backend when a reading is evaluated. */
export interface VitalAnomaly {
  type: VitalType;
  value: number;
  severity: AnomalySeverity;
  direction: AnomalyDirection;
  message: string;
  recordedAt: ISODateTime;
}

/** @projection Backend ward overview row. */
export interface WardVitalsRow {
  patient: PatientSummary;
  latest?: VitalSigns;
  anomalies: VitalAnomaly[];
  /** @viewerScoped @projection Minutes since `latest.recordedAt`, relative to "now". */
  lastMeasuredAgoMin?: number;
}
