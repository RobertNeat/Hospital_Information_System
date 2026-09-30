// Mock implementation; backend authoritative - see docs/contract/CONVENTIONS.md.
import { VITAL_THRESHOLDS } from '../constants/vitals-thresholds';
import type { ISODateTime, VitalAnomaly, VitalSigns, VitalType } from '../models';

const VITAL_LABELS: Record<VitalType, string> = {
  systolic: 'Ciśnienie skurczowe',
  diastolic: 'Ciśnienie rozkurczowe',
  heartRate: 'Tętno',
  temperature: 'Temperatura',
  spo2: 'Saturacja SpO₂',
  respiratoryRate: 'Częstość oddechów',
};

function evaluateOne(type: VitalType, value: number, recordedAt: ISODateTime): VitalAnomaly | null {
  const t = VITAL_THRESHOLDS[type];
  const label = VITAL_LABELS[type];

  if (value < t.criticalLow) {
    return {
      type,
      value,
      severity: 'critical',
      direction: 'low',
      message: `${label}: wartość krytycznie niska (${value} ${t.unit}).`,
      recordedAt,
    };
  }
  if (value > t.criticalHigh) {
    return {
      type,
      value,
      severity: 'critical',
      direction: 'high',
      message: `${label}: wartość krytycznie wysoka (${value} ${t.unit}).`,
      recordedAt,
    };
  }
  if (value < t.low) {
    return {
      type,
      value,
      severity: 'warning',
      direction: 'low',
      message: `${label}: wartość poniżej normy (${value} ${t.unit}).`,
      recordedAt,
    };
  }
  if (value > t.high) {
    return {
      type,
      value,
      severity: 'warning',
      direction: 'high',
      message: `${label}: wartość powyżej normy (${value} ${t.unit}).`,
      recordedAt,
    };
  }
  return null;
}

/** Evaluates every present vital field of `v` against `VITAL_THRESHOLDS`. */
export function evaluateVitals(v: VitalSigns): VitalAnomaly[] {
  const anomalies: VitalAnomaly[] = [];
  const fields: VitalType[] = [
    'systolic',
    'diastolic',
    'heartRate',
    'temperature',
    'spo2',
    'respiratoryRate',
  ];
  for (const field of fields) {
    const value = v[field];
    if (value === undefined || value === null) continue;
    const anomaly = evaluateOne(field, value, v.recordedAt);
    if (anomaly) anomalies.push(anomaly);
  }
  return anomalies;
}
