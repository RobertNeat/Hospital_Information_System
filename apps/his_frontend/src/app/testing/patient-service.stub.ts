import { of, throwError } from 'rxjs';
import { PATIENTS } from '../mock-data/patients.mock';
import { WARDS } from '../mock-data/wards.mock';
import type { AdmitPatientRequest, Patient, PatientDraft, PatientSearchQuery } from '../models';
import { PatientService } from '../services/patient.service';
import { toPatientSummary } from '../utils/patient-summary';

const fold = (s: string): string => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();

/** In-memory `PatientService` double over the mock patients (fresh state per TestBed). */
export function createPatientServiceStub(): Partial<Record<keyof PatientService, unknown>> {
  const patients: Patient[] = structuredClone(PATIENTS);
  const find = (id: string): Patient | undefined => patients.find((p) => p.id === id);
  const notFound = (id: string) => throwError(() => new Error(`Nie znaleziono pacjenta ${id}`));

  const getPatients = (q?: PatientSearchQuery) => {
    const term = q?.term ? fold(q.term) : '';
    const result = patients.filter(
      (p) =>
        (!q?.status || p.status === q.status) &&
        (!q?.wardId || (p.status === 'admitted' && p.currentAdmission?.wardId === q.wardId)) &&
        (!term ||
          [p.lastName, p.firstName, p.mrn, p.pesel ?? ''].some((v) => fold(v).includes(term))),
    );
    return of(result.map((p) => toPatientSummary(p, WARDS)));
  };

  return {
    getPatients,
    search: (term: string) => getPatients({ term }),
    getPatientById: (id: string) => {
      const p = find(id);
      return p ? of(p) : notFound(id);
    },
    findByPesel: (pesel: string) => {
      const p = patients.find((x) => x.pesel === pesel);
      return of(p ? toPatientSummary(p, WARDS) : null);
    },
    createPatient: (draft: PatientDraft) => {
      const now = new Date().toISOString();
      const created: Patient = {
        ...draft,
        id: `test-patient-${patients.length + 1}`,
        mrn: `HIS/TEST/${patients.length + 1}`,
        status: 'registered',
        createdAt: now,
        updatedAt: now,
        version: 0,
      };
      patients.push(created);
      return of(created);
    },
    updatePatient: (id: string, changes: Partial<Patient>) => {
      const p = find(id);
      if (!p) return notFound(id);
      Object.assign(p, changes);
      return of(p);
    },
    getAdmissions: (id: string) =>
      of(find(id)?.currentAdmission ? [find(id)!.currentAdmission!] : []),
    admitPatient: (id: string, admission: AdmitPatientRequest) => {
      const p = find(id);
      if (!p) return notFound(id);
      p.currentAdmission = {
        ...admission,
        id: `test-admission-${id}`,
        patientId: id,
        status: 'active',
      };
      p.status = admission.admissionType === 'outpatient' ? 'outpatient' : 'admitted';
      return of(p);
    },
    dischargePatient: (id: string) => {
      const p = find(id);
      if (!p) return notFound(id);
      p.status = 'discharged';
      delete p.currentAdmission;
      return of(p);
    },
  };
}

/** Test provider replacing the HTTP-backed `PatientService` with the mock patient list. */
export const patientServiceStub = {
  provide: PatientService,
  useFactory: createPatientServiceStub,
};
