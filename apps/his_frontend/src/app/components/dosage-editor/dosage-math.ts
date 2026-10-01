import type { AdministrationRoute, DoseFrequency, Drug } from '../../models';

/**
 * Times per day implied by a `DoseFrequency`, used to compute the daily dose from a
 * single-administration dose. `Q4H` is 6x/day (24/4), `QW` is 1x/week averaged to 1/7 per day.
 * `PRN` (as-needed) has no fixed frequency -- callers must use `maxPerDay` as the worst case
 * instead of this map.
 */
export const TIMES_PER_DAY: Record<Exclude<DoseFrequency, 'PRN'>, number> = {
  QD: 1,
  BID: 2,
  TID: 3,
  QID: 4,
  Q4H: 6,
  Q6H: 4,
  Q8H: 3,
  Q12H: 2,
  QW: 1 / 7,
};

export interface DailyDoseInput {
  dose: number | null;
  doseUnit: string;
  frequency: DoseFrequency | null;
  asNeeded: boolean;
  maxPerDay: number | null;
}

/**
 * Daily dose in `doseUnit`, or `null` when there isn't enough information yet.
 * For PRN (as-needed) dosing there is no fixed frequency, so the worst-case daily
 * exposure is `dose x maxPerDay` (falls back to a single dose when `maxPerDay` is unset).
 */
export function computeDailyDose(input: DailyDoseInput): number | null {
  const { dose, frequency, asNeeded, maxPerDay } = input;
  if (dose === null || dose === undefined || Number.isNaN(dose)) return null;

  if (asNeeded) {
    return maxPerDay && maxPerDay > 0 ? dose * maxPerDay : dose;
  }
  if (!frequency || frequency === 'PRN') return null;
  return dose * TIMES_PER_DAY[frequency];
}

/**
 * Warns when the computed daily dose exceeds `drug.maxDailyDose`, ONLY when the dose unit
 * matches the max-dose unit exactly (mixed units, e.g. mg vs mcg, are not converted).
 * Instant UI hint while editing the dosage; the backend repeats the check (`max_dose`) in
 * `POST /drug-safety-checks` when the dosage is sent.
 */
export function exceedsMaxDailyDose(
  dailyDose: number | null,
  drug: Pick<Drug, 'maxDailyDose'>,
): boolean {
  if (dailyDose === null || !drug.maxDailyDose) return false;
  return dailyDose > drug.maxDailyDose.value;
}

/** Leading numeric strength per dose unit, e.g. "5 mg" -> 5, "875/125 mg" -> 875, "1%" -> null. */
export function parseStrengthValue(strength: string, doseUnit: string): number | null {
  const escapedUnit = doseUnit.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = strength.match(new RegExp(`^([\\d.]+)(?:/[\\d.]+)?\\s*${escapedUnit}\\b`, 'i'));
  if (!match) return null;
  const value = Number(match[1]);
  return Number.isFinite(value) && value > 0 ? value : null;
}

/**
 * Suggested package quantity = ceil(unitsPerDay x days / packageSize), where unitsPerDay is
 * the number of dose-units (tablets, capsules, doses...) taken per day -- NOT the milligram
 * total. We derive units/day by dividing the daily dose (in mg/mcg/etc.) by the per-unit
 * strength parsed from `drug.strength` when the units match; otherwise we fall back to
 * treating `dose` itself as a unit count (e.g. "1 dawka", "2 kaps."). Minimum 1 package.
 */
export function suggestPackageQuantity(
  dailyDose: number | null,
  dose: number | null,
  drug: Pick<Drug, 'strength' | 'packageSize' | 'defaultDoseUnit'>,
  days: number | null,
): number {
  if (!dailyDose || !days || days <= 0 || !drug.packageSize) return 1;

  const strengthPerUnit = parseStrengthValue(drug.strength, drug.defaultDoseUnit);
  const unitsPerDay = strengthPerUnit ? dailyDose / strengthPerUnit : (dose ?? dailyDose);

  const totalUnits = unitsPerDay * days;
  const packages = Math.ceil(totalUnits / drug.packageSize);
  return Math.max(1, packages);
}

/** Default dose unit and route for a newly selected drug. */
export function defaultRoute(drug: Pick<Drug, 'routes'>): AdministrationRoute | null {
  return drug.routes[0] ?? null;
}
