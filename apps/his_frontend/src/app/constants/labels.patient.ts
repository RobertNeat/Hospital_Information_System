import type { SelectOption } from '../models';
import { toOptions } from './labels.utils';

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
