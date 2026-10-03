import { of, throwError } from 'rxjs';
import { PRESCRIPTIONS } from '../mock-data/prescriptions.mock';
import type { ActiveMedication, Prescription } from '../models';
import type { PrescriptionCreateRequest, PrescriptionFilter } from '../models/api';
import { PrescriptionService } from '../services/prescription.service';

/** In-memory `PrescriptionService` double over the mock prescriptions (fresh state per TestBed). */
export function createPrescriptionServiceStub(): Partial<
  Record<keyof PrescriptionService, unknown>
> {
  const prescriptions: Prescription[] = structuredClone(PRESCRIPTIONS);
  const notFound = (id: string) => throwError(() => new Error(`Nie znaleziono recepty ${id}`));

  return {
    getPrescriptions: (filter?: PrescriptionFilter) =>
      of(
        prescriptions.filter(
          (p) =>
            (!filter?.patientId || p.patientId === filter.patientId) &&
            (!filter?.prescriberId || p.prescriberId === filter.prescriberId) &&
            (!filter?.status || p.status === filter.status) &&
            (!filter?.kind || p.kind === filter.kind),
        ),
      ),
    getById: (id: string) => {
      const found = prescriptions.find((p) => p.id === id);
      return found ? of(found) : notFound(id);
    },
    getActiveMedications: (pid: string) => {
      const today = new Date().toISOString().slice(0, 10);
      return of(
        prescriptions
          .filter(
            (p) =>
              p.patientId === pid &&
              (p.status === 'issued' || p.status === 'partially_dispensed') &&
              p.validUntil >= today,
          )
          .flatMap((p): ActiveMedication[] =>
            p.items.map((item) => ({ ...item, prescriptionId: p.id, date: p.validFrom })),
          ),
      );
    },
    issuePrescription: (draft: PrescriptionCreateRequest) => {
      const prescription: Prescription = {
        ...draft,
        id: `test-rx-${prescriptions.length + 1}`,
        prescriberId: 'test-staff',
        issuedAt: new Date().toISOString(),
        status: 'issued',
        accessCode: '1234',
        eRxKey: 'K'.repeat(44),
      };
      prescriptions.push(prescription);
      return of(prescription);
    },
    cancel: (id: string, reason?: string) => {
      const index = prescriptions.findIndex((p) => p.id === id);
      if (index === -1) return notFound(id);
      prescriptions[index] = {
        ...prescriptions[index],
        status: 'cancelled',
        cancelledAt: new Date().toISOString(),
        ...(reason ? { cancelReason: reason } : {}),
      };
      return of(prescriptions[index]);
    },
  };
}

/** Test provider replacing the HTTP-backed `PrescriptionService` with the mock prescriptions. */
export const prescriptionServiceStub = {
  provide: PrescriptionService,
  useFactory: createPrescriptionServiceStub,
};
