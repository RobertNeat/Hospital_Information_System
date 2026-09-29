import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import type { Gender, ISODate } from '../models';

const WEIGHTS = [1, 3, 7, 9, 1, 3, 7, 9, 1, 3];

/** Result of parsing a valid PESEL's embedded birth date and gender. */
export interface ParsedPesel {
  birthDate: ISODate;
  gender: Gender;
}

/**
 * Validates the PESEL checksum (11 digits, weighted-sum algorithm) AND that the
 * embedded date is a real calendar date (rejects e.g. a Feb-30 encoding).
 */
export function isValidPesel(pesel: string): boolean {
  if (!/^\d{11}$/.test(pesel)) return false;

  const digits = pesel.split('').map(Number);
  const sum = digits.slice(0, 10).reduce((acc, d, i) => acc + d * WEIGHTS[i], 0);
  const checkDigit = (10 - (sum % 10)) % 10;
  if (checkDigit !== digits[10]) return false;

  return parsePesel(pesel) !== null;
}

/**
 * Extracts birth date and gender from a PESEL, without checking the checksum.
 * Returns `null` if the embedded date is not a valid calendar date.
 *
 * Month encoding: 1900s -> month unchanged (01-12), 2000s -> month + 20 (21-32),
 * 2100s -> month + 40, 1800s -> month + 80, 2200s -> month + 60 (rare, included
 * for completeness though outside any plausible demo data range).
 */
export function parsePesel(pesel: string): ParsedPesel | null {
  if (!/^\d{11}$/.test(pesel)) return null;

  const yy = Number(pesel.slice(0, 2));
  const rawMonth = Number(pesel.slice(2, 4));
  const dd = Number(pesel.slice(4, 6));

  let century: number;
  let month: number;
  if (rawMonth >= 81 && rawMonth <= 92) {
    century = 1800;
    month = rawMonth - 80;
  } else if (rawMonth >= 1 && rawMonth <= 12) {
    century = 1900;
    month = rawMonth;
  } else if (rawMonth >= 21 && rawMonth <= 32) {
    century = 2000;
    month = rawMonth - 20;
  } else if (rawMonth >= 41 && rawMonth <= 52) {
    century = 2100;
    month = rawMonth - 40;
  } else if (rawMonth >= 61 && rawMonth <= 72) {
    century = 2200;
    month = rawMonth - 60;
  } else {
    return null;
  }

  const year = century + yy;
  const date = new Date(year, month - 1, dd);
  const isRealDate =
    date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === dd;
  if (!isRealDate) return null;

  const genderDigit = Number(pesel[9]);
  const gender: Gender = genderDigit % 2 === 1 ? 'male' : 'female';

  const mm = String(month).padStart(2, '0');
  const ddStr = String(dd).padStart(2, '0');
  const birthDate: ISODate = `${year}-${mm}-${ddStr}`;

  return { birthDate, gender };
}

/** Reactive Forms validator wrapping `isValidPesel`. Empty values pass (pair with `Validators.required`). */
export function peselValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as string | null;
    if (!value) return null;
    return isValidPesel(value) ? null : { pesel: true };
  };
}
