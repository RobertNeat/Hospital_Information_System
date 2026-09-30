import { toOptions } from './labels.utils';

// ---- Drug / prescription ----

export const DRUG_FORM_LABELS: Record<
  | 'tablet'
  | 'capsule'
  | 'injection'
  | 'syrup'
  | 'drops'
  | 'ointment'
  | 'inhaler'
  | 'suppository'
  | 'patch',
  string
> = {
  tablet: 'Tabletka',
  capsule: 'Kapsułka',
  injection: 'Iniekcja',
  syrup: 'Syrop',
  drops: 'Krople',
  ointment: 'Maść',
  inhaler: 'Inhalator',
  suppository: 'Czopek',
  patch: 'Plaster',
};
export const DRUG_FORM_OPTIONS = toOptions(DRUG_FORM_LABELS);

export const ROUTE_LABELS: Record<
  'oral' | 'sublingual' | 'iv' | 'im' | 'sc' | 'topical' | 'inhalation' | 'rectal' | 'transdermal',
  string
> = {
  oral: 'Doustnie',
  sublingual: 'Podjęzykowo',
  iv: 'Dożylnie',
  im: 'Domięśniowo',
  sc: 'Podskórnie',
  topical: 'Miejscowo',
  inhalation: 'Wziewnie',
  rectal: 'Doodbytniczo',
  transdermal: 'Przezskórnie',
};
export const ROUTE_OPTIONS = toOptions(ROUTE_LABELS);

export const REIMBURSEMENT_LABELS: Record<'100%' | '50%' | '30%' | 'R' | 'B' | 'none', string> = {
  '100%': '100%',
  '50%': '50%',
  '30%': '30%',
  R: 'Ryczałt',
  B: 'Bezpłatny',
  none: 'Pełnopłatny',
};
export const REIMBURSEMENT_OPTIONS = toOptions(REIMBURSEMENT_LABELS);

export const FREQUENCY_LABELS: Record<
  'QD' | 'BID' | 'TID' | 'QID' | 'Q4H' | 'Q6H' | 'Q8H' | 'Q12H' | 'QW' | 'PRN',
  string
> = {
  QD: '1x dziennie',
  BID: '2x dziennie',
  TID: '3x dziennie',
  QID: '4x dziennie',
  Q4H: 'co 4 h',
  Q6H: 'co 6 h',
  Q8H: 'co 8 h',
  Q12H: 'co 12 h',
  QW: '1x w tygodniu',
  PRN: 'Doraźnie',
};
export const FREQUENCY_OPTIONS = toOptions(FREQUENCY_LABELS);

export const TIME_OF_DAY_LABELS: Record<'morning' | 'noon' | 'evening' | 'night', string> = {
  morning: 'Rano',
  noon: 'W południe',
  evening: 'Wieczorem',
  night: 'Na noc',
};
export const TIME_OF_DAY_OPTIONS = toOptions(TIME_OF_DAY_LABELS);

export const PRESCRIPTION_STATUS_LABELS: Record<
  'issued' | 'partially_dispensed' | 'dispensed' | 'cancelled' | 'expired',
  string
> = {
  issued: 'Wystawiona',
  partially_dispensed: 'Częściowo zrealizowana',
  dispensed: 'Zrealizowana',
  cancelled: 'Anulowana',
  expired: 'Wygasła',
};
export const PRESCRIPTION_STATUS_OPTIONS = toOptions(PRESCRIPTION_STATUS_LABELS);

export const PRESCRIPTION_KIND_LABELS: Record<'e_prescription' | 'hospital_order', string> = {
  e_prescription: 'e-Recepta',
  hospital_order: 'Zlecenie szpitalne',
};
export const PRESCRIPTION_KIND_OPTIONS = toOptions(PRESCRIPTION_KIND_LABELS);

// ---- Vitals ----

export const VITAL_CONTEXT_LABELS: Record<
  'office_exam' | 'ward_round' | 'triage' | 'observation',
  string
> = {
  office_exam: 'Badanie ambulatoryjne',
  ward_round: 'Obchód',
  triage: 'Triage',
  observation: 'Obserwacja',
};
export const VITAL_CONTEXT_OPTIONS = toOptions(VITAL_CONTEXT_LABELS);
