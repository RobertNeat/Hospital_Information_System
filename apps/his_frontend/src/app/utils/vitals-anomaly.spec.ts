import { describe, expect, it } from 'vitest';
import { VITAL_THRESHOLDS } from '../mock-data/vital-thresholds.mock';
import { classifyVital, type VitalThresholds } from './vitals-anomaly';

const thresholds: VitalThresholds = Object.fromEntries(VITAL_THRESHOLDS.map((t) => [t.type, t]));

describe('classifyVital', () => {
  it('returns nothing for a normal value and at the band edges (strict comparisons)', () => {
    expect(classifyVital(thresholds, 'systolic', 120)).toBeUndefined();
    expect(classifyVital(thresholds, 'systolic', 140)).toBeUndefined();
    expect(classifyVital(thresholds, 'spo2', 100)).toBeUndefined();
  });

  it('flags warnings and criticals with direction', () => {
    expect(classifyVital(thresholds, 'systolic', 150)).toEqual({
      severity: 'warning',
      direction: 'high',
    });
    expect(classifyVital(thresholds, 'heartRate', 45)).toEqual({
      severity: 'warning',
      direction: 'low',
    });
    expect(classifyVital(thresholds, 'spo2', 85)).toEqual({
      severity: 'critical',
      direction: 'low',
    });
    expect(classifyVital(thresholds, 'temperature', 40)).toEqual({
      severity: 'critical',
      direction: 'high',
    });
  });

  it('ignores missing values and unloaded thresholds', () => {
    expect(classifyVital(thresholds, 'heartRate', undefined)).toBeUndefined();
    expect(classifyVital({}, 'heartRate', 200)).toBeUndefined();
  });
});
