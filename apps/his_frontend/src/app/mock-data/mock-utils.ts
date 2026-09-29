import type { ISODateTime } from '../models';

/** ISO datetime `n` days ago at `hour:00` local time, relative to now. Keeps demo data "current". */
export function daysAgo(n: number, hour = 8): ISODateTime {
  const d = new Date();
  d.setDate(d.getDate() - n);
  d.setHours(hour, 0, 0, 0);
  return d.toISOString();
}

/** ISO datetime `n` days ahead at `hour:00` local time, relative to now. */
export function daysAhead(n: number, hour = 8): ISODateTime {
  const d = new Date();
  d.setDate(d.getDate() + n);
  d.setHours(hour, 0, 0, 0);
  return d.toISOString();
}

/** ISO datetime `n` hours ago, relative to now. */
export function hoursAgo(n: number): ISODateTime {
  const d = new Date();
  d.setHours(d.getHours() - n);
  return d.toISOString();
}

/** ISO datetime `n` days ago at an explicit hour/minute, for building repeatable series. */
export function isoAt(daysBack: number, hour: number, minute = 0): ISODateTime {
  const d = new Date();
  d.setDate(d.getDate() - daysBack);
  d.setHours(hour, minute, 0, 0);
  return d.toISOString();
}

/** Deterministic seeded PRNG (mulberry32), for reproducible mock series across runs. */
export function seeded(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Random float in `[min, max]` using a seeded RNG function, rounded to `decimals`. */
export function seededRange(rng: () => number, min: number, max: number, decimals = 0): number {
  const value = min + rng() * (max - min);
  const factor = 10 ** decimals;
  return Math.round(value * factor) / factor;
}
