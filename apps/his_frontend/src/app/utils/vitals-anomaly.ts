import type { AnomalyDirection, AnomalySeverity, VitalThreshold, VitalType } from '../models';

export type VitalThresholds = Partial<Record<VitalType, VitalThreshold>>;

export interface VitalClassification {
  severity: AnomalySeverity;
  direction: AnomalyDirection;
}

/**
 * Display-only classification of one value against backend thresholds (strict comparisons, as on
 * the server). Colours history cells and trend points, where the backend returns no anomalies.
 * Anomalies of a saved reading and of the ward overview always come from the backend.
 */
export function classifyVital(
  thresholds: VitalThresholds,
  type: VitalType,
  value: number | null | undefined,
): VitalClassification | undefined {
  const t = thresholds[type];
  if (!t || value === undefined || value === null) return undefined;
  if (value < t.criticalLow) return { severity: 'critical', direction: 'low' };
  if (value > t.criticalHigh) return { severity: 'critical', direction: 'high' };
  if (value < t.low) return { severity: 'warning', direction: 'low' };
  if (value > t.high) return { severity: 'warning', direction: 'high' };
  return undefined;
}
