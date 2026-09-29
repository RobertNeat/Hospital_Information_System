import type { ISODate, ISODateTime } from '../models';

/** Age in whole years, as of `now` (defaults to the current date). */
export function ageFromBirthDate(birthDate: ISODate, now: Date = new Date()): number {
  const birth = new Date(birthDate);
  let age = now.getFullYear() - birth.getFullYear();
  const monthDiff = now.getMonth() - birth.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < birth.getDate())) {
    age--;
  }
  return age;
}

export function addDays(date: Date | ISODate | ISODateTime, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

export function startOfDay(date: Date | ISODate | ISODateTime = new Date()): Date {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  return d;
}

/** Minutes elapsed between `at` and `now` (defaults to the current time). */
export function minutesAgo(at: ISODateTime, now: Date = new Date()): number {
  return Math.round((now.getTime() - new Date(at).getTime()) / 60000);
}
