import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { PATIENTS } from '../mock-data/patients.mock';
import { WARDS } from '../mock-data/wards.mock';
import type {
  Admission,
  AdmitPatientRequest,
  DischargeOptions,
  ID,
  Patient,
  PatientDraft,
  PatientSearchQuery,
  PatientSummary,
} from '../models';
import { mockError, mockResponse, nextId } from '../utils/mock-response';
import { toPatientSummary } from '../utils/patient-summary';

function normalize(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

@Injectable({ providedIn: 'root' })
export class PatientService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly patients: Patient[] = structuredClone(PATIENTS);
  private sequence = this.patients.length;
  private readonly admissions: Admission[] = [];
  private admissionSequence = 0;

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
    return mockResponse(
      result.map((p) => toPatientSummary(p, WARDS)),
      this.latency,
    );
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
    return mockResponse(found ? toPatientSummary(found, WARDS) : null, this.latency);
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

  /** Admission history of a patient, newest first. */
  getAdmissions(patientId: ID): Observable<Admission[]> {
    const patient = this.patients.find((p) => p.id === patientId);
    if (!patient) return mockError(`Nie znaleziono pacjenta o id ${patientId}`, this.latency);
    const stored = this.admissions.filter((a) => a.patientId === patientId);
    const history = stored.length ? stored : this.synthesizeFromCurrent(patient);
    return mockResponse(
      history.map((a) => ({ ...a })).sort((a, b) => b.admittedAt.localeCompare(a.admittedAt)),
      this.latency,
    );
  }

  admitPatient(id: string, admission: AdmitPatientRequest): Observable<Patient> {
    const index = this.patients.findIndex((p) => p.id === id);
    if (index === -1) return mockError(`Nie znaleziono pacjenta o id ${id}`, this.latency);
    const patient = this.patients[index];
    this.ensureHistory(patient);
    // At most one active admission per patient: close any previous one.
    for (const previous of this.admissions) {
      if (previous.patientId === id && previous.status === 'active') {
        previous.status = 'discharged';
        previous.dischargedAt ??= admission.admittedAt;
      }
    }
    this.admissionSequence++;
    const created: Admission = {
      ...admission,
      id: nextId('adm', this.admissionSequence),
      patientId: id,
      status: 'active',
    };
    this.admissions.push(created);
    const updated: Patient = {
      ...patient,
      status: admission.admissionType === 'outpatient' ? 'outpatient' : 'admitted',
      currentAdmission: { ...created },
      updatedAt: new Date().toISOString(),
    };
    this.patients[index] = updated;
    return mockResponse(updated, this.latency);
  }

  dischargePatient(id: string, at: string, options?: DischargeOptions): Observable<Patient> {
    const index = this.patients.findIndex((p) => p.id === id);
    if (index === -1) return mockError(`Nie znaleziono pacjenta o id ${id}`, this.latency);
    const current = this.patients[index];
    this.ensureHistory(current);
    const active = this.admissions.find((a) => a.patientId === id && a.status === 'active');
    const closing: Partial<Admission> = {
      dischargedAt: at,
      ...(options?.disposition && { dischargeDisposition: options.disposition }),
      ...(options?.summaryNoteId && { dischargeSummaryNoteId: options.summaryNoteId }),
    };
    if (active) {
      Object.assign(active, closing, { status: 'discharged' as const });
    }
    const updated: Patient = {
      ...current,
      status: 'discharged',
      currentAdmission: current.currentAdmission
        ? {
            ...current.currentAdmission,
            ...closing,
            ...(active && { status: 'discharged' as const }),
          }
        : current.currentAdmission,
      updatedAt: new Date().toISOString(),
    };
    this.patients[index] = updated;
    return mockResponse(updated, this.latency);
  }

  /** Builds a history entry from a mock `currentAdmission` that has no stored record. */
  private synthesizeFromCurrent(patient: Patient): Admission[] {
    const current = patient.currentAdmission;
    if (!current) return [];
    return [
      {
        ...current,
        id: current.id ?? `adm-${patient.id}`,
        patientId: patient.id,
        status: current.status ?? (current.dischargedAt ? 'discharged' : 'active'),
      },
    ];
  }

  /** Materializes the synthesized entry so later changes are tracked in the history. */
  private ensureHistory(patient: Patient): void {
    if (this.admissions.some((a) => a.patientId === patient.id)) return;
    this.admissions.push(...this.synthesizeFromCurrent(patient));
  }
}
