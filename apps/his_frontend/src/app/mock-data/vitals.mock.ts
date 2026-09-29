import type { VitalSigns } from '../models';
import { hoursAgo, seeded, seededRange } from './mock-utils';

let counter = 0;
function nextVitalId(): string {
  counter++;
  return `vit-${String(counter).padStart(3, '0')}`;
}

function buildSeries(
  patientId: string,
  recordedById: string,
  context: VitalSigns['context'],
  hoursBackList: number[],
  seed: number,
  overrides: Partial<VitalSigns>[] = [],
): VitalSigns[] {
  const rng = seeded(seed);
  return hoursBackList.map((h, i) => {
    const base: VitalSigns = {
      id: nextVitalId(),
      patientId,
      recordedAt: hoursAgo(h),
      recordedById,
      context,
      systolic: seededRange(rng, 110, 135),
      diastolic: seededRange(rng, 70, 85),
      heartRate: seededRange(rng, 65, 90),
      temperature: seededRange(rng, 36.3, 37.0, 1),
      spo2: seededRange(rng, 95, 99),
      respiratoryRate: seededRange(rng, 14, 18),
      painScore: seededRange(rng, 0, 2),
    };
    return { ...base, ...overrides[i] };
  });
}

// pat-001: admitted, heart failure - readings every ~5h over 7 days, some elevated BP
export const VITALS_PAT_001: VitalSigns[] = buildSeries(
  'pat-001',
  'stf-006',
  'ward_round',
  [4, 9, 14, 19, 24, 34, 44, 54, 64, 74, 84, 94, 104, 114, 124, 134, 144, 154, 164],
  1,
).map((v, i) => (i === 2 ? { ...v, systolic: 152, diastolic: 96, heartRate: 98 } : v));

// pat-002: admitted, ACS - includes a critical hypotension + tachycardia episode
export const VITALS_PAT_002: VitalSigns[] = buildSeries(
  'pat-002',
  'stf-007',
  'ward_round',
  [2, 8, 14, 20, 26],
  2,
).map((v, i) => (i === 1 ? { ...v, systolic: 78, diastolic: 48, heartRate: 134, spo2: 91 } : v));

// pat-004: admitted, stroke - fever anomaly
export const VITALS_PAT_004: VitalSigns[] = buildSeries(
  'pat-004',
  'stf-009',
  'ward_round',
  [3, 9, 15, 21, 27, 39, 51, 63, 75, 87, 99, 111],
  4,
).map((v, i) => (i === 4 ? { ...v, temperature: 39.8 } : v));

// pat-007: SOR, trauma - critical SpO2 drop
export const VITALS_PAT_007: VitalSigns[] = buildSeries(
  'pat-007',
  'stf-010',
  'triage',
  [1, 3, 5],
  7,
).map((v, i) => (i === 0 ? { ...v, spo2: 89, respiratoryRate: 28, heartRate: 125 } : v));

// pat-009: admitted, pre-op - stable
export const VITALS_PAT_009: VitalSigns[] = buildSeries(
  'pat-009',
  'stf-008',
  'ward_round',
  [5, 15, 25, 35, 45],
  9,
);

// pat-012: admitted, CKD - hypertensive crisis anomaly (BP 185/110)
export const VITALS_PAT_012: VitalSigns[] = buildSeries(
  'pat-012',
  'stf-006',
  'ward_round',
  [4, 10, 16, 22, 28, 40, 52, 64, 76, 88, 100, 112, 124, 136, 148, 160],
  12,
).map((v, i) => (i === 3 ? { ...v, systolic: 185, diastolic: 110, heartRate: 102 } : v));

// pat-013: SOR, appendicitis
export const VITALS_PAT_013: VitalSigns[] = buildSeries(
  'pat-013',
  'stf-010',
  'triage',
  [1, 2],
  13,
).map((v, i) => (i === 0 ? { ...v, temperature: 38.3, heartRate: 105 } : v));

// pat-015: admitted post-appendectomy - stable
export const VITALS_PAT_015: VitalSigns[] = buildSeries(
  'pat-015',
  'stf-008',
  'ward_round',
  [4, 12, 20],
  15,
);

// Sparse office-exam readings for outpatients over ~6 months
export const VITALS_PAT_003: VitalSigns[] = buildSeries(
  'pat-003',
  'stf-001',
  'office_exam',
  [24 * 30, 24 * 90, 24 * 180],
  3,
);
export const VITALS_PAT_008: VitalSigns[] = buildSeries(
  'pat-008',
  'stf-001',
  'office_exam',
  [24 * 20, 24 * 60, 24 * 150],
  8,
);
export const VITALS_PAT_010: VitalSigns[] = buildSeries(
  'pat-010',
  'stf-001',
  'office_exam',
  [24 * 10, 24 * 45],
  10,
);
export const VITALS_PAT_014: VitalSigns[] = buildSeries(
  'pat-014',
  'stf-001',
  'office_exam',
  [24 * 5, 24 * 40],
  14,
);

export const VITALS: VitalSigns[] = [
  ...VITALS_PAT_001,
  ...VITALS_PAT_002,
  ...VITALS_PAT_004,
  ...VITALS_PAT_007,
  ...VITALS_PAT_009,
  ...VITALS_PAT_012,
  ...VITALS_PAT_013,
  ...VITALS_PAT_015,
  ...VITALS_PAT_003,
  ...VITALS_PAT_008,
  ...VITALS_PAT_010,
  ...VITALS_PAT_014,
].sort((a, b) => new Date(a.recordedAt).getTime() - new Date(b.recordedAt).getTime());
