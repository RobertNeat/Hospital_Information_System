import type { Allergy, ClinicalNote, Diagnosis } from '../ehr.model';

/** `authorId` is omitted: the author always comes from the session (anti-spoofing). */
export type ClinicalNoteCreateRequest = Omit<
  ClinicalNote,
  'id' | 'authorId' | 'createdAt' | 'createdById' | 'updatedAt' | 'updatedById' | 'version'
>;

/**
 * DiagnosisCreateRequest z kontraktu. `diagnosedById` jest ignorowany i w pelni pominiety (aktor z sesji);
 * brak `diagnosedAt` = teraz, brak `status` = active. `code` musi byc SNOMED/SCTID (422 w innym przypadku).
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
  Partial<Pick<Diagnosis, 'diagnosedAt' | 'status'>>;

/**
 * AllergyCreateRequest z kontraktu. `recordedById` jest ignorowany i w pelni pominiety (aktor z sesji);
 * brak `recordedAt` = teraz, brak `status` = active.
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
  | 'recordedById'
> &
  Partial<Pick<Allergy, 'recordedAt' | 'status'>>;
