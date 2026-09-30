import type { Allergy, ClinicalNote, Diagnosis } from '../ehr.model';

export type ClinicalNoteCreateRequest = Omit<
  ClinicalNote,
  'id' | 'createdAt' | 'createdById' | 'updatedAt' | 'updatedById' | 'version'
>;

export type DiagnosisCreateRequest = Omit<
  Diagnosis,
  'id' | 'createdAt' | 'createdById' | 'updatedAt' | 'updatedById' | 'version'
>;

export type AllergyCreateRequest = Omit<
  Allergy,
  'id' | 'createdAt' | 'createdById' | 'updatedAt' | 'updatedById' | 'version'
>;
