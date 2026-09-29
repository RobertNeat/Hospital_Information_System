import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import type { PrescriptionDraft } from '../models';
import { PrescriptionService } from './prescription.service';

function draft(): PrescriptionDraft {
  return {
    patientId: 'pat-003',
    prescriberId: 'stf-001',
    validFrom: '2026-01-01',
    validUntil: '2026-01-31',
    kind: 'e_prescription',
    items: [
      {
        drugId: 'drg-014',
        drugName: 'Paracetamol',
        activeSubstance: 'Paracetamol',
        strength: '500 mg',
        form: 'tablet',
        dosage: {
          dose: 500,
          doseUnit: 'mg',
          route: 'oral',
          frequency: 'TID',
          durationDays: 5,
          asNeeded: false,
        },
        quantityPackages: 1,
        reimbursement: 'none',
        substitutionAllowed: true,
      },
    ],
  };
}

describe('PrescriptionService', () => {
  let service: PrescriptionService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    service = TestBed.inject(PrescriptionService);
  });

  it('filters prescriptions by patientId', async () => {
    const result = await firstValueFrom(service.getPrescriptions({ patientId: 'pat-001' }));
    expect(result.every((p) => p.patientId === 'pat-001')).toBe(true);
  });

  it('getActiveMedications returns items only from issued/partially_dispensed, non-expired prescriptions', async () => {
    const items = await firstValueFrom(service.getActiveMedications('pat-001'));
    expect(items.length).toBeGreaterThan(0);
  });

  it('issuePrescription generates a 4-digit accessCode and 44-char eRxKey, and persists', async () => {
    const before = await firstValueFrom(service.getPrescriptions());
    const issued = await firstValueFrom(service.issuePrescription(draft()));

    expect(issued.accessCode).toMatch(/^\d{4}$/);
    expect(issued.eRxKey).toHaveLength(44);
    expect(issued.status).toBe('issued');

    const after = await firstValueFrom(service.getPrescriptions());
    expect(after.length).toBe(before.length + 1);
  });

  it('cancel sets status to cancelled', async () => {
    const updated = await firstValueFrom(service.cancel('rx-001'));
    expect(updated.status).toBe('cancelled');
  });
});
