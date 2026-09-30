import type { Auditable, Coding, ID, ISODateTime, Versioned } from './common.model';
import type { OrderStatus, OrderUrgency, StatusChange } from './lab.model';

/** Labels: USG, RTG, TK, RM, Mammografia, Endoskopia/Gastroskopia, Kolonoskopia, Angiografia. */
export type ImagingModality =
  'USG' | 'RTG' | 'CT' | 'MRI' | 'MMG' | 'ENDOSCOPY' | 'COLONOSCOPY' | 'ANGIOGRAPHY';

export interface ImagingExam {
  code: string;
  modality: ImagingModality;
  name: string;
  bodyRegion: string;
  contrastPossible: boolean;
  requiresLaterality: boolean;
  preparation?: string;
  durationMinutes: number;
}

export type Laterality = 'left' | 'right' | 'bilateral' | 'na';

export type PregnancyStatus = 'no' | 'yes' | 'unknown' | 'na';

export interface SafetyChecklist {
  pregnancy: PregnancyStatus;
  pacemakerOrImplant: boolean;
  metalFragments: boolean;
  contrastAllergy: boolean;
  creatinine?: number;
  egfr?: number;
  claustrophobia: boolean;
  confirmed: boolean;
}

/** Out of contract priority: the slot entity will be modelled together with the scheduling module. */
export interface ScheduleSlot {
  id: ID;
  modality: ImagingModality;
  start: ISODateTime;
  end: ISODateTime;
  room: string;
  available: boolean;
}

export interface ImagingOrder extends Versioned, Partial<Auditable> {
  id: ID;
  patientId: ID;
  encounterId?: ID;
  examCode: string;
  /** @snapshot Exam name at the time of ordering. */
  examName: string;
  /** @snapshot Modality of the exam at the time of ordering. */
  modality: ImagingModality;
  /** @snapshot Body region of the exam at the time of ordering. */
  bodyRegion: string;
  laterality: Laterality;
  contrast: boolean;
  clinicalIndication: string;
  clinicalQuestion?: string;
  diagnosisCode?: Coding;
  urgency: OrderUrgency;
  safety: SafetyChecklist;
  slotId?: ID;
  scheduledAt?: ISODateTime;
  orderedById: ID;
  orderedAt: ISODateTime;
  /** Changed only through actions (updateStatus / cancel); the actor comes from the session. */
  status: OrderStatus;
  statusHistory: StatusChange[];
}

/** @deprecated Use `ImagingOrderCreateRequest` from `models/api`. */
export type ImagingOrderDraft = Omit<
  ImagingOrder,
  | 'id'
  | 'orderedAt'
  | 'status'
  | 'statusHistory'
  | 'version'
  | 'createdAt'
  | 'createdById'
  | 'updatedAt'
  | 'updatedById'
>;

export type ImagingResultStatus = 'preliminary' | 'final';

export interface ImagingResult {
  id: ID;
  patientId: ID;
  /** Required for internal orders, optional for external ones. */
  orderId?: ID;
  modality: ImagingModality;
  examName: string;
  bodyRegion: string;
  performedAt: ISODateTime;
  reportedAt: ISODateTime;
  /** @snapshot Radiologist name at the time of reporting. */
  radiologistName: string;
  radiologistId?: ID;
  technique?: string;
  findings: string;
  conclusion: string;
  status: ImagingResultStatus;
  /**
   * Number of images in the study (display only).
   * DICOM attachments and images are by design not implemented in this application.
   */
  imageCount: number;
  /** Flag set by the radiologist (not derived from the findings). */
  critical: boolean;
  /** Set when the result is acknowledged (see `ResultReview`). */
  reviewedAt?: ISODateTime;
  reviewedById?: ID;
}
