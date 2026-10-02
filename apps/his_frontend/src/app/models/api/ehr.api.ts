import type { Allergy, ClinicalNote, Diagnosis } from '../ehr.model';

export type ClinicalNoteCreateRequest = Omit<
  ClinicalNote,
  'id' | 'createdAt' | 'createdById' | 'updatedAt' | 'updatedById' | 'version'
>;

/**
 * DiagnosisCreateRequest z kontraktu. `diagnosedById` jest ignorowany (aktor z sesji); brak `diagnosedAt` = teraz,
 * brak `status` = active. `code` musi byc SNOMED/SCTID (422 w innym przypadku).
 */
export type DiagnosisCreateRequest = Omit<
  Diagnosis,
  | 'id'
  | 'createdAt'
  | 'createdById'
  | 'updatedAt'
  | 'updatedById'
  | 'version'
  | 'diagnosedAt'
  | 'diagnosedById'
  | 'status'
> &
  Partial<Pick<Diagnosis, 'diagnosedAt' | 'diagnosedById' | 'status'>>;

/**
 * AllergyCreateRequest z kontraktu. `recordedById` jest ignorowany (aktor z sesji); brak `recordedAt` = teraz,
 * brak `status` = active.
 */
export type AllergyCreateRequest = Omit<
  Allergy,
  | 'id'
  | 'createdAt'
  | 'createdById'
  | 'updatedAt'
  | 'updatedById'
  | 'version'
  | 'recordedAt'
  | 'status'
> &
  Partial<Pick<Allergy, 'recordedAt' | 'status'>>;
