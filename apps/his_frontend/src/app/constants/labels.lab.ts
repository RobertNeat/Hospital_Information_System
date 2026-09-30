import { toOptions } from './labels.utils';

// ---- Lab ----

export const URGENCY_LABELS: Record<'routine' | 'urgent' | 'stat', string> = {
  routine: 'Rutynowe',
  urgent: 'Pilne',
  stat: 'Natychmiastowe (CITO)',
};
export const URGENCY_OPTIONS = toOptions(URGENCY_LABELS);

export const SPECIMEN_LABELS: Record<
  'blood' | 'serum' | 'urine' | 'stool' | 'swab' | 'csf' | 'tissue',
  string
> = {
  blood: 'Krew pełna',
  serum: 'Surowica',
  urine: 'Mocz',
  stool: 'Kał',
  swab: 'Wymaz',
  csf: 'Płyn mózgowo-rdzeniowy',
  tissue: 'Wycinek tkankowy',
};
export const SPECIMEN_OPTIONS = toOptions(SPECIMEN_LABELS);

export const LAB_CATEGORY_LABELS: Record<
  | 'hematology'
  | 'biochemistry'
  | 'coagulation'
  | 'immunology'
  | 'urinalysis'
  | 'microbiology'
  | 'pathology',
  string
> = {
  hematology: 'Hematologia',
  biochemistry: 'Biochemia',
  coagulation: 'Koagulologia',
  immunology: 'Immunologia',
  urinalysis: 'Badanie moczu',
  microbiology: 'Mikrobiologia',
  pathology: 'Patomorfologia',
};
export const LAB_CATEGORY_OPTIONS = toOptions(LAB_CATEGORY_LABELS);

export const ORDER_STATUS_LABELS: Record<
  'ordered' | 'scheduled' | 'specimen_collected' | 'in_progress' | 'completed' | 'cancelled',
  string
> = {
  ordered: 'Zlecone',
  scheduled: 'Zaplanowane',
  specimen_collected: 'Pobrano materiał',
  in_progress: 'W trakcie',
  completed: 'Zakończone',
  cancelled: 'Anulowane',
};
export const ORDER_STATUS_OPTIONS = toOptions(ORDER_STATUS_LABELS);

export const RESULT_FLAG_LABELS: Record<'N' | 'L' | 'H' | 'LL' | 'HH' | 'A', string> = {
  N: 'W normie',
  L: 'Niskie',
  H: 'Wysokie',
  LL: 'Krytycznie niskie',
  HH: 'Krytycznie wysokie',
  A: 'Nieprawidłowe',
};
export const RESULT_FLAG_OPTIONS = toOptions(RESULT_FLAG_LABELS);

export const RESULT_STATUS_LABELS: Record<'preliminary' | 'final' | 'corrected', string> = {
  preliminary: 'Wstępny',
  final: 'Ostateczny',
  corrected: 'Skorygowany',
};
export const RESULT_STATUS_OPTIONS = toOptions(RESULT_STATUS_LABELS);

// ---- Imaging ----

export const IMAGING_MODALITY_LABELS: Record<
  'USG' | 'RTG' | 'CT' | 'MRI' | 'MMG' | 'ENDOSCOPY' | 'COLONOSCOPY' | 'ANGIOGRAPHY',
  string
> = {
  USG: 'USG',
  RTG: 'RTG',
  CT: 'TK',
  MRI: 'RM',
  MMG: 'Mammografia',
  ENDOSCOPY: 'Endoskopia/Gastroskopia',
  COLONOSCOPY: 'Kolonoskopia',
  ANGIOGRAPHY: 'Angiografia',
};
export const IMAGING_MODALITY_OPTIONS = toOptions(IMAGING_MODALITY_LABELS);

export const LATERALITY_LABELS: Record<'left' | 'right' | 'bilateral' | 'na', string> = {
  left: 'Lewa',
  right: 'Prawa',
  bilateral: 'Obustronnie',
  na: 'Nie dotyczy',
};
export const LATERALITY_OPTIONS = toOptions(LATERALITY_LABELS);
