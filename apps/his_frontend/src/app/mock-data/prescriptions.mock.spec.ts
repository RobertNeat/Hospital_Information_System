import { describe, expect, it } from 'vitest';
import { PRESCRIPTIONS } from './prescriptions.mock';

describe('PRESCRIPTIONS mock data', () => {
  it('every prescription has a 4-digit accessCode', () => {
    for (const rx of PRESCRIPTIONS) {
      expect(rx.accessCode, `${rx.id} accessCode`).toMatch(/^\d{4}$/);
    }
  });

  it('every prescription has a 44-character eRxKey', () => {
    for (const rx of PRESCRIPTIONS) {
      expect(rx.eRxKey, `${rx.id} eRxKey length`).toHaveLength(44);
    }
  });

  it('has unique ids', () => {
    const ids = new Set(PRESCRIPTIONS.map((p) => p.id));
    expect(ids.size).toBe(PRESCRIPTIONS.length);
  });
});
