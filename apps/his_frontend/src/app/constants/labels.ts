import type { SelectOption } from '../models';

function toOptions<T extends string>(labels: Record<T, string>): SelectOption<T>[] {
  return (Object.keys(labels) as T[]).map((value) => ({ value, label: labels[value] }));
}

// ---- Patient ----

export const ADMISSION_STATUS_LABELS: Record<
  'registered' | 'admitted' | 'outpatient' | 'discharged',
  string
> = {
  registered: 'Zarejestrowany',
  admitted: 'Przyjęty',
  outpatient: 'Ambulatoryjny',
  discharged: 'Wypisany',
};
export const ADMISSION_STATUS_OPTIONS = toOptions(ADMISSION_STATUS_LABELS);

export const TRIAGE_LABELS: Record<'red' | 'orange' | 'yellow' | 'green' | 'blue', string> = {
  red: 'Czerwony (natychmiastowy)',
  orange: 'Pomarańczowy (bardzo pilny)',
  yellow: 'Żółty (pilny)',
  green: 'Zielony (standardowy)',
  blue: 'Niebieski (odroczony)',
};
export const TRIAGE_OPTIONS = toOptions(TRIAGE_LABELS);

export const PATIENT_FLAG_LABELS: Record<
  'isolation' | 'fall_risk' | 'dnr' | 'infection_risk' | 'vip',
  string
> = {
  isolation: 'Izolacja',
  fall_risk: 'Ryzyko upadku',
  dnr: 'DNR',
  infection_risk: 'Ryzyko zakażenia',
  vip: 'VIP',
};
export const PATIENT_FLAG_OPTIONS = toOptions(PATIENT_FLAG_LABELS);

export const GENDER_LABELS: Record<'female' | 'male' | 'other' | 'unknown', string> = {
  female: 'Kobieta',
  male: 'Mężczyzna',
  other: 'Inna',
  unknown: 'Nieznana',
};
export const GENDER_OPTIONS = toOptions(GENDER_LABELS);

export const BLOOD_TYPE_OPTIONS: SelectOption[] = [
  'A+',
  'A-',
  'B+',
  'B-',
  'AB+',
  'AB-',
  '0+',
  '0-',
].map((v) => ({ label: v, value: v }));

export const NO_PESEL_REASON_LABELS: Record<'foreigner' | 'newborn' | 'unknown_identity', string> =
  {
    foreigner: 'Cudzoziemiec',
    newborn: 'Noworodek',
    unknown_identity: 'Tożsamość nieustalona',
  };
export const NO_PESEL_REASON_OPTIONS = toOptions(NO_PESEL_REASON_LABELS);

export const IDENTITY_DOCUMENT_TYPE_LABELS: Record<'id_card' | 'passport' | 'other', string> = {
  id_card: 'Dowód osobisty',
  passport: 'Paszport',
  other: 'Inny dokument',
};
export const IDENTITY_DOCUMENT_TYPE_OPTIONS = toOptions(IDENTITY_DOCUMENT_TYPE_LABELS);

export const INSURANCE_STATUS_LABELS: Record<'active' | 'inactive' | 'unknown', string> = {
  active: 'Aktywne',
  inactive: 'Nieaktywne',
  unknown: 'Nieznane',
};
export const INSURANCE_STATUS_OPTIONS = toOptions(INSURANCE_STATUS_LABELS);

export const INSURANCE_PAYER_LABELS: Record<'NFZ' | 'private' | 'none', string> = {
  NFZ: 'NFZ',
  private: 'Prywatne',
  none: 'Brak',
};
export const INSURANCE_PAYER_OPTIONS = toOptions(INSURANCE_PAYER_LABELS);

export const ADMISSION_TYPE_LABELS: Record<
  'planned' | 'emergency' | 'transfer' | 'outpatient',
  string
> = {
  planned: 'Planowe',
  emergency: 'Nagłe (SOR)',
  transfer: 'Przeniesienie',
  outpatient: 'Tylko rejestracja ambulatoryjna',
};
export const ADMISSION_TYPE_OPTIONS = toOptions(ADMISSION_TYPE_LABELS);

/** 16 voivodeship NFZ branches. */
export const NFZ_BRANCH_OPTIONS: SelectOption[] = [
  { value: '01', label: '01 – Dolnośląski OW NFZ' },
  { value: '02', label: '02 – Kujawsko-Pomorski OW NFZ' },
  { value: '03', label: '03 – Lubelski OW NFZ' },
  { value: '04', label: '04 – Lubuski OW NFZ' },
  { value: '05', label: '05 – Łódzki OW NFZ' },
  { value: '06', label: '06 – Małopolski OW NFZ' },
  { value: '07', label: '07 – Mazowiecki OW NFZ' },
  { value: '08', label: '08 – Opolski OW NFZ' },
  { value: '09', label: '09 – Podkarpacki OW NFZ' },
  { value: '10', label: '10 – Podlaski OW NFZ' },
  { value: '11', label: '11 – Pomorski OW NFZ' },
  { value: '12', label: '12 – Śląski OW NFZ' },
  { value: '13', label: '13 – Świętokrzyski OW NFZ' },
  { value: '14', label: '14 – Warmińsko-Mazurski OW NFZ' },
  { value: '15', label: '15 – Wielkopolski OW NFZ' },
  { value: '16', label: '16 – Zachodniopomorski OW NFZ' },
];

// ---- EHR ----

export const ENCOUNTER_TYPE_LABELS: Record<
  'visit' | 'consultation' | 'hospitalization' | 'emergency' | 'teleconsultation',
  string
> = {
  visit: 'Wizyta',
  consultation: 'Konsultacja',
  hospitalization: 'Hospitalizacja',
  emergency: 'Nagły przypadek',
  teleconsultation: 'Teleporada',
};
export const ENCOUNTER_TYPE_OPTIONS = toOptions(ENCOUNTER_TYPE_LABELS);

export const ENCOUNTER_STATUS_LABELS: Record<
  'planned' | 'in_progress' | 'finished' | 'cancelled',
  string
> = {
  planned: 'Zaplanowana',
  in_progress: 'W trakcie',
  finished: 'Zakończona',
  cancelled: 'Anulowana',
};
export const ENCOUNTER_STATUS_OPTIONS = toOptions(ENCOUNTER_STATUS_LABELS);

export const NOTE_CATEGORY_LABELS: Record<
  'admission' | 'progress' | 'consultation' | 'nursing' | 'observation' | 'discharge',
  string
> = {
  admission: 'Przyjęcie',
  progress: 'Przebieg',
  consultation: 'Konsultacja',
  nursing: 'Pielęgniarska',
  observation: 'Obserwacja',
  discharge: 'Wypis',
};
export const NOTE_CATEGORY_OPTIONS = toOptions(NOTE_CATEGORY_LABELS);

export const DIAGNOSIS_TYPE_LABELS: Record<'primary' | 'secondary' | 'chronic', string> = {
  primary: 'Główne',
  secondary: 'Współistniejące',
  chronic: 'Przewlekłe',
};
export const DIAGNOSIS_TYPE_OPTIONS = toOptions(DIAGNOSIS_TYPE_LABELS);

export const DIAGNOSIS_STATUS_LABELS: Record<'active' | 'resolved', string> = {
  active: 'Aktywne',
  resolved: 'Ustąpiło',
};
export const DIAGNOSIS_STATUS_OPTIONS = toOptions(DIAGNOSIS_STATUS_LABELS);

export const ALLERGY_CATEGORY_LABELS: Record<'drug' | 'food' | 'environment' | 'other', string> = {
  drug: 'Lek',
  food: 'Pokarm',
  environment: 'Środowiskowa',
  other: 'Inna',
};
export const ALLERGY_CATEGORY_OPTIONS = toOptions(ALLERGY_CATEGORY_LABELS);

export const ALLERGY_SEVERITY_LABELS: Record<
  'mild' | 'moderate' | 'severe' | 'life_threatening',
  string
> = {
  mild: 'Łagodna',
  moderate: 'Umiarkowana',
  severe: 'Ciężka',
  life_threatening: 'Zagrażająca życiu',
};
export const ALLERGY_SEVERITY_OPTIONS = toOptions(ALLERGY_SEVERITY_LABELS);

export const TREATMENT_TYPE_LABELS: Record<
  'pharmacotherapy' | 'procedure' | 'surgery' | 'rehabilitation' | 'other',
  string
> = {
  pharmacotherapy: 'Farmakoterapia',
  procedure: 'Zabieg',
  surgery: 'Operacja',
  rehabilitation: 'Rehabilitacja',
  other: 'Inne',
};
export const TREATMENT_TYPE_OPTIONS = toOptions(TREATMENT_TYPE_LABELS);

export const TREATMENT_STATUS_LABELS: Record<'ongoing' | 'completed' | 'discontinued', string> = {
  ongoing: 'W trakcie',
  completed: 'Zakończone',
  discontinued: 'Przerwane',
};
export const TREATMENT_STATUS_OPTIONS = toOptions(TREATMENT_STATUS_LABELS);

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

// ---- Messaging ----

export const PRIORITY_LABELS: Record<'normal' | 'high' | 'critical', string> = {
  normal: 'Normalny',
  high: 'Wysoki',
  critical: 'Krytyczny',
};
export const PRIORITY_OPTIONS = toOptions(PRIORITY_LABELS);

export const TASK_STATUS_LABELS: Record<'open' | 'in_progress' | 'done' | 'cancelled', string> = {
  open: 'Otwarte',
  in_progress: 'W trakcie',
  done: 'Zakończone',
  cancelled: 'Anulowane',
};
export const TASK_STATUS_OPTIONS = toOptions(TASK_STATUS_LABELS);

export const SHIFT_LABELS: Record<'day' | 'night', string> = {
  day: 'Dzienna',
  night: 'Nocna',
};
export const SHIFT_OPTIONS = toOptions(SHIFT_LABELS);

export const ALERT_TYPE_LABELS: Record<
  'critical_result' | 'vital_anomaly' | 'order_status' | 'task' | 'system',
  string
> = {
  critical_result: 'Krytyczny wynik',
  vital_anomaly: 'Odchylenie parametrów',
  order_status: 'Status zlecenia',
  task: 'Zadanie',
  system: 'Systemowy',
};
export const ALERT_TYPE_OPTIONS = toOptions(ALERT_TYPE_LABELS);

export const ALERT_SEVERITY_LABELS: Record<'info' | 'warning' | 'critical', string> = {
  info: 'Informacja',
  warning: 'Ostrzeżenie',
  critical: 'Krytyczny',
};
export const ALERT_SEVERITY_OPTIONS = toOptions(ALERT_SEVERITY_LABELS);

export const STAFF_ROLE_LABELS: Record<'doctor' | 'nurse', string> = {
  doctor: 'Lekarz',
  nurse: 'Pielęgniarka/Pielęgniarz',
};
export const STAFF_ROLE_OPTIONS = toOptions(STAFF_ROLE_LABELS);
