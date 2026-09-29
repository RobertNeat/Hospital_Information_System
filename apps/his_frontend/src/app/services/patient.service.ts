import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { PATIENTS } from '../mock-data/patients.mock';
import { WARDS } from '../mock-data/wards.mock';
import type {
  Admission,
  Patient,
  PatientDraft,
  PatientSearchQuery,
  PatientSummary,
} from '../models';
import { mockError, mockResponse, nextId } from '../utils/mock-response';

function normalize(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

function toSummary(p: Patient): PatientSummary {
  // Only show ward/bed for patients who are currently admitted -- a discharged
  // patient can still carry a stale currentAdmission with a wardId/bed.
  const ward =
    p.status === 'admitted' && p.currentAdmission
      ? WARDS.find((w) => w.id === p.currentAdmission!.wardId)
      : undefined;
  return {
    id: p.id,
    mrn: p.mrn,
    pesel: p.pesel,
    firstName: p.firstName,
    lastName: p.lastName,
    birthDate: p.birthDate,
    gender: p.gender,
    status: p.status,
    flags: p.flags,
    wardName: ward?.name,
    bed: p.status === 'admitted' ? p.currentAdmission?.bed : undefined,
  };
}

@Injectable({ providedIn: 'root' })
export class PatientService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly patients: Patient[] = structuredClone(PATIENTS);
  private sequence = this.patients.length;

  getPatients(q?: PatientSearchQuery): Observable<PatientSummary[]> {
    let result = this.patients;
    if (q?.status) {
      result = result.filter((p) => p.status === q.status);
    }
    if (q?.wardId) {
      // A discharged patient can still carry a stale currentAdmission.wardId, so a ward
      // filter must also require the patient to be currently admitted.
      result = result.filter(
        (p) => p.status === 'admitted' && p.currentAdmission?.wardId === q.wardId,
      );
    }
    if (q?.term) {
      const term = normalize(q.term);
      result = result.filter(
        (p) =>
          normalize(p.lastName).includes(term) ||
          normalize(p.firstName).includes(term) ||
          (p.pesel ?? '').includes(q.term!) ||
          normalize(p.mrn).includes(term),
      );
    }
    return mockResponse(result.map(toSummary), this.latency);
  }

  /** Matches last name, first name, PESEL and MRN, case- and diacritics-insensitive. */
  search(term: string): Observable<PatientSummary[]> {
    return this.getPatients({ term });
  }

  getPatientById(id: string): Observable<Patient> {
    const found = this.patients.find((p) => p.id === id);
    if (!found) return mockError(`Nie znaleziono pacjenta o id ${id}`, this.latency);
    return mockResponse(found, this.latency);
  }

  /** Duplicate check by PESEL. */
  findByPesel(pesel: string): Observable<PatientSummary | null> {
    const found = this.patients.find((p) => p.pesel === pesel);
    return mockResponse(found ? toSummary(found) : null, this.latency);
  }

  createPatient(draft: PatientDraft): Observable<Patient> {
    this.sequence++;
    const now = new Date().toISOString();
    const patient: Patient = {
      ...draft,
      id: nextId('pat', this.sequence),
      mrn: `HIS/${new Date().getFullYear()}/${String(this.sequence).padStart(6, '0')}`,
      createdAt: now,
      updatedAt: now,
    };
    this.patients.push(patient);
    return mockResponse(patient, this.latency);
  }

  updatePatient(id: string, changes: Partial<PatientDraft>): Observable<Patient> {
    const index = this.patients.findIndex((p) => p.id === id);
    if (index === -1) return mockError(`Nie znaleziono pacjenta o id ${id}`, this.latency);
    const updated: Patient = {
      ...this.patients[index],
      ...changes,
      updatedAt: new Date().toISOString(),
    };
    this.patients[index] = updated;
    return mockResponse(updated, this.latency);
  }

  admitPatient(id: string, admission: Admission): Observable<Patient> {
    const index = this.patients.findIndex((p) => p.id === id);
    if (index === -1) return mockError(`Nie znaleziono pacjenta o id ${id}`, this.latency);
    const updated: Patient = {
      ...this.patients[index],
      status: admission.admissionType === 'outpatient' ? 'outpatient' : 'admitted',
      currentAdmission: admission,
      updatedAt: new Date().toISOString(),
    };
    this.patients[index] = updated;
    return mockResponse(updated, this.latency);
  }

  dischargePatient(id: string, at: string): Observable<Patient> {
    const index = this.patients.findIndex((p) => p.id === id);
    if (index === -1) return mockError(`Nie znaleziono pacjenta o id ${id}`, this.latency);
    const current = this.patients[index];
    const updated: Patient = {
      ...current,
      status: 'discharged',
      currentAdmission: current.currentAdmission
        ? { ...current.currentAdmission, dischargedAt: at }
        : current.currentAdmission,
      updatedAt: new Date().toISOString(),
    };
    this.patients[index] = updated;
    return mockResponse(updated, this.latency);
  }
}
