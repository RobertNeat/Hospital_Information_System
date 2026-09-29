import { describe, expect, it } from 'vitest';
import { FormControl } from '@angular/forms';
import { isValidPesel, parsePesel, peselValidator } from './pesel.validator';

describe('isValidPesel', () => {
  it('accepts a valid 1900s male PESEL', () => {
    expect(isValidPesel('68031437976')).toBe(true);
  });

  it('accepts a valid 2000s female PESEL', () => {
    expect(isValidPesel('96071114225')).toBe(true);
  });

  it('rejects a bad checksum', () => {
    expect(isValidPesel('68031437977')).toBe(false);
  });

  it('rejects a non-11-digit string', () => {
    expect(isValidPesel('1234567890')).toBe(false);
    expect(isValidPesel('123456789012')).toBe(false);
    expect(isValidPesel('abcdefghijk')).toBe(false);
  });

  it('rejects an impossible calendar date (Feb 30) even with a correct checksum', () => {
    // yy=99 mm=02 dd=30 -> digits 9902 30 xxx c ; construct one with a valid checksum
    // 99 02 30 001 1 -> compute check digit
    const partial = '9902300011'; // 10 digits, gender digit=1 (male)
    const weights = [1, 3, 7, 9, 1, 3, 7, 9, 1, 3];
    const digits = partial.split('').map(Number);
    const sum = digits.reduce((acc, d, i) => acc + d * weights[i], 0);
    const check = (10 - (sum % 10)) % 10;
    const pesel = partial + String(check);
    expect(isValidPesel(pesel)).toBe(false);
  });
});

describe('parsePesel', () => {
  it('extracts birthDate and gender for a 1900s male', () => {
    expect(parsePesel('68031437976')).toEqual({ birthDate: '1968-03-14', gender: 'male' });
  });

  it('extracts birthDate and gender for a 2000s female', () => {
    expect(parsePesel('96071114225')).toEqual({ birthDate: '1996-07-11', gender: 'female' });
  });

  it('extracts birthDate and gender for a 2000s male born in 2001', () => {
    expect(parsePesel('01230324175')).toEqual({ birthDate: '2001-03-03', gender: 'male' });
  });

  it('returns null for malformed input', () => {
    expect(parsePesel('not-a-pesel')).toBeNull();
  });
});

describe('peselValidator', () => {
  it('passes for empty value (pair with Validators.required separately)', () => {
    const control = new FormControl('');
    expect(peselValidator()(control)).toBeNull();
  });

  it('passes for a valid PESEL', () => {
    const control = new FormControl('68031437976');
    expect(peselValidator()(control)).toBeNull();
  });

  it('fails for an invalid PESEL', () => {
    const control = new FormControl('00000000000');
    expect(peselValidator()(control)).toEqual({ pesel: true });
  });
});
