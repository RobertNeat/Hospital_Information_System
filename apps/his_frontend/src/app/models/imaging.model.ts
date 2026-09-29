import type { Coding, ID, ISODateTime } from './common.model';
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

export interface SafetyChecklist {
  pregnancy: 'no' | 'yes' | 'unknown' | 'na';
  pacemakerOrImplant: boolean;
  metalFragments: boolean;
  contrastAllergy: boolean;
  creatinine?: number;
  egfr?: number;
  claustrophobia: boolean;
  confirmed: boolean;
}

export interface ScheduleSlot {
  id: ID;
  modality: ImagingModality;
  start: ISODateTime;
  end: ISODateTime;
  room: string;
  available: boolean;
}

export interface ImagingOrder {
  id: ID;
  patientId: ID;
  examCode: string;
  examName: string;
  modality: ImagingModality;
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
  status: OrderStatus;
  statusHistory: StatusChange[];
}

export type ImagingOrderDraft = Omit<ImagingOrder, 'id' | 'orderedAt' | 'status' | 'statusHistory'>;

export interface ImagingResult {
  id: ID;
  patientId: ID;
  orderId?: ID;
  modality: ImagingModality;
  examName: string;
  bodyRegion: string;
  performedAt: ISODateTime;
  reportedAt: ISODateTime;
  radiologistName: string;
  technique?: string;
  findings: string;
  conclusion: string;
  status: 'preliminary' | 'final';
  imageCount: number;
  critical: boolean;
}
