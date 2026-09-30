import { toOptions } from './labels.utils';

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

export const ALLERGY_STATUS_LABELS: Record<'active' | 'resolved' | 'inactive', string> = {
  active: 'Aktywna',
  resolved: 'Ustąpiła',
  inactive: 'Nieaktywna',
};

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
