import type { Patient, PatientSummary, Ward } from '../models';

/** Projects a Patient to its list/header summary, resolving the ward name from `wards`. */
export function toPatientSummary(p: Patient, wards: readonly Ward[]): PatientSummary {
  // Only show ward/bed for patients who are currently admitted -- a discharged
  // patient can still carry a stale currentAdmission with a wardId/bed.
  const ward =
    p.status === 'admitted' && p.currentAdmission
      ? wards.find((w) => w.id === p.currentAdmission!.wardId)
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
