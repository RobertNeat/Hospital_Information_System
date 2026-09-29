import { describe, expect, it } from 'vitest';
import { isValidPesel, parsePesel } from '../validators/pesel.validator';
import { PATIENTS } from './patients.mock';

describe('PATIENTS mock data', () => {
  it('has 15 patients', () => {
    expect(PATIENTS).toHaveLength(15);
  });

  it('every patient PESEL passes isValidPesel, or is null with noPeselReason set', () => {
    for (const p of PATIENTS) {
      if (p.pesel === null) {
        expect(p.noPeselReason, `${p.id} has no PESEL but no noPeselReason`).toBeTruthy();
        continue;
      }
      expect(isValidPesel(p.pesel), `${p.id} PESEL ${p.pesel} is not valid`).toBe(true);
    }
  });

  it('every PESEL-parsed birthDate and gender matches the patient record', () => {
    for (const p of PATIENTS) {
      if (p.pesel === null) continue;
      const parsed = parsePesel(p.pesel);
      expect(parsed, `${p.id} PESEL did not parse`).not.toBeNull();
      expect(parsed!.birthDate, `${p.id} birthDate mismatch`).toBe(p.birthDate);
      expect(parsed!.gender, `${p.id} gender mismatch`).toBe(p.gender);
    }
  });

  it('has exactly one patient without a PESEL (the foreigner)', () => {
    const withoutPesel = PATIENTS.filter((p) => p.pesel === null);
    expect(withoutPesel).toHaveLength(1);
    expect(withoutPesel[0].noPeselReason).toBe('foreigner');
  });

  it('has unique ids and MRNs', () => {
    const ids = new Set(PATIENTS.map((p) => p.id));
    const mrns = new Set(PATIENTS.map((p) => p.mrn));
    expect(ids.size).toBe(PATIENTS.length);
    expect(mrns.size).toBe(PATIENTS.length);
  });
});
