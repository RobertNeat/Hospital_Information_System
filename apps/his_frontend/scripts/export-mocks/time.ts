import { raw, type Raw } from './sql';

/**
 * Fixed "now" of the generator: 2026-06-15 12:00 Europe/Warsaw (10:00Z). Midday, so that
 * `daysAgo(0)` (08:00 local) is in the past and every mock difference is a whole number of minutes.
 */
export const T0 = new Date('2026-06-15T10:00:00.000Z');
export const T0_LOCAL_DATE = '2026-06-15';
export const TIME_ZONE = 'Europe/Warsaw';

/**
 * Instant relative to the moment of migration: `now() - interval '76 hours 30 minutes'`. The offset
 * from T0 is preserved exactly (so every CHECK on ordering stays true), while the file text does not
 * depend on when the generator ran.
 */
export function ts(iso: string): Raw {
  const ms = Date.parse(iso);
  if (Number.isNaN(ms)) throw new Error(`invalid ISO datetime: ${iso}`);
  const deltaSec = Math.round((ms - T0.getTime()) / 1000);
  if (deltaSec === 0) return raw('now()');
  const abs = Math.abs(deltaSec);
  const parts: string[] = [];
  const h = Math.floor(abs / 3600);
  const m = Math.floor((abs % 3600) / 60);
  const s = abs % 60;
  if (h) parts.push(`${h} hours`);
  if (m) parts.push(`${m} minutes`);
  if (s) parts.push(`${s} seconds`);
  return raw(`now() ${deltaSec < 0 ? '-' : '+'} interval '${parts.join(' ')}'`);
}

export const tsOpt = (iso: string | undefined | null): Raw | null => (iso ? ts(iso) : null);

/** Calendar date relative to the migration day: `CURRENT_DATE - 3`. Input: `YYYY-MM-DD`. */
export function relDate(isoDate: string): Raw {
  const day = isoDate.slice(0, 10);
  const diff = Math.round(
    (Date.parse(`${day}T00:00:00Z`) - Date.parse(`${T0_LOCAL_DATE}T00:00:00Z`)) / 86400000,
  );
  if (diff === 0) return raw('CURRENT_DATE');
  return raw(`CURRENT_DATE ${diff < 0 ? '-' : '+'} ${Math.abs(diff)}`);
}

/** `YYYY-MM-DD` of T0 shifted by `days` (pure calendar arithmetic, no time zone involved). */
export function addDaysToT0Date(days: number): string {
  const d = new Date(`${T0_LOCAL_DATE}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
