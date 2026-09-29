import { describe, expect, it } from 'vitest';
import type { VitalSigns } from '../models';
import { evaluateVitals } from './vitals-anomaly';

function baseVitals(overrides: Partial<VitalSigns> = {}): VitalSigns {
  return {
    id: 'vit-test',
    patientId: 'pat-001',
    recordedAt: '2026-01-01T08:00:00.000Z',
    recordedById: 'stf-001',
    context: 'ward_round',
    systolic: 120,
    diastolic: 78,
    heartRate: 72,
    temperature: 36.6,
    spo2: 98,
    respiratoryRate: 16,
    ...overrides,
  };
}

describe('evaluateVitals', () => {
  it('returns no anomalies for normal vitals', () => {
    expect(evaluateVitals(baseVitals())).toEqual([]);
  });

  it('flags a warning for slightly elevated systolic pressure', () => {
    const anomalies = evaluateVitals(baseVitals({ systolic: 150 }));
    expect(anomalies).toHaveLength(1);
    expect(anomalies[0]).toMatchObject({
      type: 'systolic',
      severity: 'warning',
      direction: 'high',
    });
  });

  it('flags a critical anomaly for very high systolic pressure', () => {
    const anomalies = evaluateVitals(baseVitals({ systolic: 185 }));
    expect(anomalies[0]).toMatchObject({
      type: 'systolic',
      severity: 'critical',
      direction: 'high',
    });
  });

  it('flags a critical anomaly for low SpO2', () => {
    const anomalies = evaluateVitals(baseVitals({ spo2: 89 }));
    expect(anomalies[0]).toMatchObject({ type: 'spo2', severity: 'critical', direction: 'low' });
  });

  it('flags a warning for slightly low SpO2', () => {
    const anomalies = evaluateVitals(baseVitals({ spo2: 92 }));
    expect(anomalies[0]).toMatchObject({ type: 'spo2', severity: 'warning', direction: 'low' });
  });

  it('flags fever as a warning and very high fever as critical', () => {
    expect(evaluateVitals(baseVitals({ temperature: 38.0 }))[0]).toMatchObject({
      severity: 'warning',
    });
    expect(evaluateVitals(baseVitals({ temperature: 40.0 }))[0]).toMatchObject({
      severity: 'critical',
    });
  });

  it('ignores fields that are undefined', () => {
    const anomalies = evaluateVitals(
      baseVitals({ painScore: undefined, respiratoryRate: undefined }),
    );
    expect(anomalies).toEqual([]);
  });

  it('can return multiple anomalies at once', () => {
    const anomalies = evaluateVitals(baseVitals({ systolic: 185, spo2: 88, heartRate: 135 }));
    expect(anomalies.map((a) => a.type).sort()).toEqual(['heartRate', 'spo2', 'systolic']);
  });

  it('treats SpO2 100 (the physical maximum) as normal, not critical', () => {
    expect(evaluateVitals(baseVitals({ spo2: 100 }))).toEqual([]);
  });

  it('treats SpO2 exactly at the warning threshold (94) as normal, and 90 as a warning (not critical)', () => {
    expect(evaluateVitals(baseVitals({ spo2: 94 }))).toEqual([]);
    const anomalies = evaluateVitals(baseVitals({ spo2: 90 }));
    expect(anomalies[0]).toMatchObject({ type: 'spo2', severity: 'warning', direction: 'low' });
  });

  it('treats systolic exactly at the critical boundary (180) as a warning, not critical', () => {
    const anomalies = evaluateVitals(baseVitals({ systolic: 180 }));
    expect(anomalies[0]).toMatchObject({
      type: 'systolic',
      severity: 'warning',
      direction: 'high',
    });
  });

  it('treats systolic just above the critical boundary (181) as critical', () => {
    const anomalies = evaluateVitals(baseVitals({ systolic: 181 }));
    expect(anomalies[0]).toMatchObject({
      type: 'systolic',
      severity: 'critical',
      direction: 'high',
    });
  });
});
