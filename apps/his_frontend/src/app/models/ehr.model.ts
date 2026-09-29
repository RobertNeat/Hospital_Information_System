import type { Coding, ID, ISODateTime } from './common.model';
import type { PrescriptionItem } from './prescription.model';

export type EncounterType =
  'visit' | 'consultation' | 'hospitalization' | 'emergency' | 'teleconsultation';

export interface Encounter {
  id: ID;
  patientId: ID;
  type: EncounterType;
  status: 'planned' | 'in_progress' | 'finished' | 'cancelled';
  startAt: ISODateTime;
  endAt?: ISODateTime;
  wardId?: ID;
  practitionerId: ID;
  reason: string;
  summary?: string;
  episodeId?: ID;
}

/** Epizod leczenia. */
export interface TreatmentEpisode {
  id: ID;
  patientId: ID;
  title: string;
  startAt: ISODateTime;
  endAt?: ISODateTime;
  status: 'active' | 'closed';
  diagnosisIds: ID[];
}

export type NoteCategory =
  'admission' | 'progress' | 'consultation' | 'nursing' | 'observation' | 'discharge';

export interface ClinicalNote {
  id: ID;
  patientId: ID;
  encounterId?: ID;
  authorId: ID;
  createdAt: ISODateTime;
  category: NoteCategory;
  title: string;
  content: string;
  symptoms?: string[];
}

export interface Diagnosis {
  id: ID;
  patientId: ID;
  code: Coding;
  type: 'primary' | 'secondary' | 'chronic';
  status: 'active' | 'resolved';
  diagnosedAt: ISODateTime;
  diagnosedById: ID;
  notes?: string;
}

export interface Allergy {
  id: ID;
  patientId: ID;
  substance: string;
  category: 'drug' | 'food' | 'environment' | 'other';
  reaction: string;
  severity: 'mild' | 'moderate' | 'severe' | 'life_threatening';
  status: 'active' | 'inactive';
  recordedAt: ISODateTime;
  /** Used by the prescription allergy check. */
  atcCodes?: string[];
}

export interface Contraindication {
  id: ID;
  patientId: ID;
  description: string;
  reason: string;
  recordedAt: ISODateTime;
}

export interface Treatment {
  id: ID;
  patientId: ID;
  name: string;
  type: 'pharmacotherapy' | 'procedure' | 'surgery' | 'rehabilitation' | 'other';
  startAt: ISODateTime;
  endAt?: ISODateTime;
  status: 'ongoing' | 'completed' | 'discontinued';
  description: string;
  practitionerId: ID;
}

export interface EhrSummary {
  recentDiagnoses: Diagnosis[];
  chronicConditions: Diagnosis[];
  activeMedications: PrescriptionItem[];
  recentEncounters: Encounter[];
  allergies: Allergy[];
}
