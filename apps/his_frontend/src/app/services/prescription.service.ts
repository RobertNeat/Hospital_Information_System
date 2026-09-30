import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { PRESCRIPTIONS } from '../mock-data/prescriptions.mock';
import type { ActiveMedication, ID, Prescription, PrescriptionDraft } from '../models';
import { mockError, mockResponse, nextId } from '../utils/mock-response';

function randomAccessCode(): string {
  return String(Math.floor(1000 + Math.random() * 9000));
}

function randomERxKey(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let key = '';
  for (let i = 0; i < 44; i++) {
    key += chars[Math.floor(Math.random() * chars.length)];
  }
  return key;
}

@Injectable({ providedIn: 'root' })
export class PrescriptionService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly prescriptions: Prescription[] = structuredClone(PRESCRIPTIONS);
  private sequence = this.prescriptions.length;

  getPrescriptions(filter?: { patientId?: ID; prescriberId?: ID }): Observable<Prescription[]> {
    let result = this.prescriptions;
    if (filter?.patientId) result = result.filter((p) => p.patientId === filter.patientId);
    if (filter?.prescriberId) result = result.filter((p) => p.prescriberId === filter.prescriberId);
    return mockResponse(result, this.latency);
  }

  getById(id: ID): Observable<Prescription> {
    const found = this.prescriptions.find((p) => p.id === id);
    if (!found) return mockError(`Nie znaleziono recepty o id ${id}`, this.latency);
    return mockResponse(found, this.latency);
  }

  /** Items from every non-expired, non-cancelled prescription of the patient, with prescription id and start date. */
  getActiveMedications(pid: ID): Observable<ActiveMedication[]> {
    const today = new Date().toISOString().slice(0, 10);
    const items = this.prescriptions
      .filter(
        (p) =>
          p.patientId === pid &&
          (p.status === 'issued' || p.status === 'partially_dispensed') &&
          p.validUntil >= today,
      )
      .flatMap((p) =>
        p.items.map((item) => ({ ...item, prescriptionId: p.id, date: p.validFrom })),
      );
    return mockResponse(items, this.latency);
  }

  issuePrescription(draft: PrescriptionDraft): Observable<Prescription> {
    this.sequence++;
    const prescription: Prescription = {
      ...draft,
      id: nextId('rx', this.sequence),
      issuedAt: new Date().toISOString(),
      status: 'issued',
      accessCode: randomAccessCode(),
      eRxKey: randomERxKey(),
    };
    this.prescriptions.push(prescription);
    return mockResponse(prescription, this.latency);
  }

  cancel(id: ID): Observable<Prescription> {
    const index = this.prescriptions.findIndex((p) => p.id === id);
    if (index === -1) return mockError(`Nie znaleziono recepty o id ${id}`, this.latency);
    const updated: Prescription = { ...this.prescriptions[index], status: 'cancelled' };
    this.prescriptions[index] = updated;
    return mockResponse(updated, this.latency);
  }
}
