import { describe, expect, it } from 'vitest';
import { addDays, ageFromBirthDate, minutesAgo, startOfDay } from './date-utils';

describe('ageFromBirthDate', () => {
  it('computes whole years as of a reference date', () => {
    expect(ageFromBirthDate('1968-03-14', new Date('2026-03-14T00:00:00'))).toBe(58);
  });

  it('has not had the birthday yet this year', () => {
    expect(ageFromBirthDate('1968-03-14', new Date('2026-03-13T00:00:00'))).toBe(57);
  });

  it('already had the birthday this year', () => {
    expect(ageFromBirthDate('1968-03-14', new Date('2026-03-15T00:00:00'))).toBe(58);
  });
});

describe('addDays', () => {
  it('adds days to a date', () => {
    const result = addDays('2026-01-01', 10);
    expect(result.getDate()).toBe(11);
  });
});

describe('startOfDay', () => {
  it('zeroes out the time component', () => {
    const result = startOfDay('2026-01-01T15:30:00');
    expect(result.getHours()).toBe(0);
    expect(result.getMinutes()).toBe(0);
  });
});

describe('minutesAgo', () => {
  it('computes elapsed minutes between two times', () => {
    const now = new Date('2026-01-01T10:30:00');
    const at = '2026-01-01T10:00:00';
    expect(minutesAgo(at, now)).toBe(30);
  });
});
