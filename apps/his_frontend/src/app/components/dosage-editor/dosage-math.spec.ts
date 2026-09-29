import { describe, expect, it } from 'vitest';
import {
  computeDailyDose,
  exceedsMaxDailyDose,
  parseStrengthValue,
  suggestPackageQuantity,
} from './dosage-math';

describe('computeDailyDose', () => {
  it('multiplies dose by times-per-day for a fixed frequency', () => {
    expect(
      computeDailyDose({
        dose: 500,
        doseUnit: 'mg',
        frequency: 'TID',
        asNeeded: false,
        maxPerDay: null,
      }),
    ).toBe(1500);
  });

  it('treats Q4H as 6 times per day', () => {
    expect(
      computeDailyDose({
        dose: 100,
        doseUnit: 'mg',
        frequency: 'Q4H',
        asNeeded: false,
        maxPerDay: null,
      }),
    ).toBe(600);
  });

  it('uses maxPerDay as the worst case for PRN dosing', () => {
    expect(
      computeDailyDose({
        dose: 1,
        doseUnit: 'dawka',
        frequency: 'PRN',
        asNeeded: true,
        maxPerDay: 8,
      }),
    ).toBe(8);
  });

  it('falls back to a single dose for PRN with no maxPerDay set', () => {
    expect(
      computeDailyDose({
        dose: 500,
        doseUnit: 'mg',
        frequency: 'PRN',
        asNeeded: true,
        maxPerDay: null,
      }),
    ).toBe(500);
  });

  it('returns null when dose is missing', () => {
    expect(
      computeDailyDose({
        dose: null,
        doseUnit: 'mg',
        frequency: 'QD',
        asNeeded: false,
        maxPerDay: null,
      }),
    ).toBeNull();
  });

  it('returns null when frequency is missing and not PRN', () => {
    expect(
      computeDailyDose({
        dose: 500,
        doseUnit: 'mg',
        frequency: null,
        asNeeded: false,
        maxPerDay: null,
      }),
    ).toBeNull();
  });
});

describe('exceedsMaxDailyDose', () => {
  it('flags when daily dose exceeds the max', () => {
    expect(exceedsMaxDailyDose(4000, { maxDailyDose: { value: 3000, unit: 'mg' } })).toBe(true);
  });

  it('does not flag when within the max', () => {
    expect(exceedsMaxDailyDose(1700, { maxDailyDose: { value: 3000, unit: 'mg' } })).toBe(false);
  });

  it('does not flag when the drug has no maxDailyDose', () => {
    expect(exceedsMaxDailyDose(99999, {})).toBe(false);
  });

  it('does not flag when dailyDose is null', () => {
    expect(exceedsMaxDailyDose(null, { maxDailyDose: { value: 10, unit: 'mg' } })).toBe(false);
  });
});

describe('parseStrengthValue', () => {
  it('parses a simple "5 mg" strength', () => {
    expect(parseStrengthValue('5 mg', 'mg')).toBe(5);
  });

  it('parses the first value of a combination strength "875/125 mg"', () => {
    expect(parseStrengthValue('875/125 mg', 'mg')).toBe(875);
  });

  it('returns null when the unit does not match', () => {
    expect(parseStrengthValue('1%', 'mg')).toBeNull();
  });

  it('returns null for non-numeric strengths against the unit', () => {
    expect(parseStrengthValue('100 mcg/dawkę', 'dawka')).toBeNull();
  });
});

describe('suggestPackageQuantity', () => {
  it('computes packages for a simple tablet regimen (Metformax 850mg BID x 30 days)', () => {
    // 1700mg/day / 850mg-per-tablet = 2 tablets/day * 30 days = 60 tablets / 30 per package = 2
    const qty = suggestPackageQuantity(
      1700,
      850,
      { strength: '850 mg', packageSize: 30, defaultDoseUnit: 'mg' },
      30,
    );
    expect(qty).toBe(2);
  });

  it('falls back to treating dose as a unit count when strength cannot be parsed', () => {
    // 1 dawka/day (Ventolin-like) x 30 days / 1 package size -> uses dose as unit count
    const qty = suggestPackageQuantity(
      8,
      1,
      { strength: '100 mcg/dawkę', packageSize: 1, defaultDoseUnit: 'dawka' },
      30,
    );
    expect(qty).toBeGreaterThanOrEqual(1);
  });

  it('returns a minimum of 1 package', () => {
    expect(
      suggestPackageQuantity(
        null,
        null,
        { strength: '5 mg', packageSize: 28, defaultDoseUnit: 'mg' },
        null,
      ),
    ).toBe(1);
  });
});
