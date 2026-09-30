import type { Auditable, Coding, ID, ISODateTime, Versioned } from './common.model';
import type { PrescriptionItem } from './prescription.model';

export type EncounterType =
  'visit' | 'consultation' | 'hospitalization' | 'emergency' | 'teleconsultation';

export type EncounterStatus = 'planned' | 'in_progress' | 'finished' | 'cancelled';

/**
 * Kontakt pacjenta ze szpitalem.
 * Encounter 1—N ClinicalNote/Diagnosis/Treatment/zlecenia/recepty przez opcjonalne `encounterId`.
 * Dla `type: 'hospitalization'` powiązany 1:1 z `Admission`.
 */
export interface Encounter {
  id: ID;
  patientId: ID;
  type: EncounterType;
  status: EncounterStatus;
  startAt: ISODateTime;
  endAt?: ISODateTime;
  wardId?: ID;
  practitionerId: ID;
  reason: string;
  summary?: string;
  /** Opcjonalny FK do `TreatmentEpisode`. */
  episodeId?: ID;
}

export type EpisodeStatus = 'active' | 'closed';

/** Epizod leczenia. */
export interface TreatmentEpisode {
  id: ID;
  patientId: ID;
  title: string;
  startAt: ISODateTime;
  endAt?: ISODateTime;
  status: EpisodeStatus;
  /** Relacja M:N z `Diagnosis` (w bazie tabela `episode_diagnosis`). */
  diagnosisIds: ID[];
}

export type NoteCategory =
  'admission' | 'progress' | 'consultation' | 'nursing' | 'observation' | 'discharge';

export interface ClinicalNote extends Auditable, Versioned {
  id: ID;
  patientId: ID;
  encounterId?: ID;
  authorId: ID;
  category: NoteCategory;
  title: string;
  content: string;
  symptoms?: string[];
}

export type DiagnosisType = 'primary' | 'secondary' | 'chronic';
export type DiagnosisStatus = 'active' | 'resolved';

export interface Diagnosis extends Partial<Auditable>, Versioned {
  id: ID;
  patientId: ID;
  encounterId?: ID;
  code: Coding;
  type: DiagnosisType;
  status: DiagnosisStatus;
  diagnosedAt: ISODateTime;
  diagnosedById: ID;
  notes?: string;
}

export type AllergyCategory = 'drug' | 'food' | 'environment' | 'other';
export type AllergySeverity = 'mild' | 'moderate' | 'severe' | 'life_threatening';
export type AllergyStatus = 'active' | 'inactive';

export interface Allergy extends Partial<Auditable>, Versioned {
  id: ID;
  patientId: ID;
  substance: string;
  category: AllergyCategory;
  reaction: string;
  severity: AllergySeverity;
  status: AllergyStatus;
  recordedAt: ISODateTime;
  recordedById?: ID;
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

export type TreatmentType =
  'pharmacotherapy' | 'procedure' | 'surgery' | 'rehabilitation' | 'other';
export type TreatmentStatus = 'ongoing' | 'completed' | 'discontinued';

export interface Treatment {
  id: ID;
  patientId: ID;
  encounterId?: ID;
  name: string;
  type: TreatmentType;
  startAt: ISODateTime;
  endAt?: ISODateTime;
  status: TreatmentStatus;
  description: string;
  practitionerId: ID;
}

/** @projection Złożony widok liczony przez backend; nie jest encją i nie jest zapisywany przez klienta. */
export interface EhrSummary {
  recentDiagnoses: Diagnosis[];
  chronicConditions: Diagnosis[];
  activeMedications: PrescriptionItem[];
  recentEncounters: Encounter[];
  allergies: Allergy[];
}
