import type { SelectOption } from '../models';

export const ACADEMIC_TITLE_OPTIONS: SelectOption<string>[] = [
  { value: 'lek.', label: 'lek. (lekarz)' },
  { value: 'lek. dent.', label: 'lek. dent. (lekarz dentysta)' },
  { value: 'lek. med. specjalista', label: 'lek. specjalista' },
  { value: 'dr n. med.', label: 'dr n. med.' },
  { value: 'dr hab. n. med.', label: 'dr hab. n. med.' },
  { value: 'prof. dr hab. n. med.', label: 'prof. dr hab. n. med.' },
  { value: 'piel.', label: 'piel. (pielęgniarka/pielęgniarz)' },
  { value: 'lic. piel.', label: 'lic. piel.' },
  { value: 'mgr piel.', label: 'mgr piel.' },
  { value: 'dr n. o zdr.', label: 'dr n. o zdrowiu' },
];

export const SPECIALIZATION_OPTIONS: SelectOption<string>[] = [
  'Anestezjologia i intensywna terapia',
  'Choroby wewnętrzne',
  'Chirurgia ogólna',
  'Diagnostyka laboratoryjna',
  'Kardiologia',
  'Medycyna ratunkowa',
  'Neurologia',
  'Onkologia kliniczna',
  'Ortopedia i traumatologia',
  'Pediatria',
  'Pielęgniarstwo internistyczne',
  'Pielęgniarstwo chirurgiczne',
  'Pielęgniarstwo anestezjologiczne',
  'Radiologia i diagnostyka obrazowa',
  'Inna',
].map((s) => ({ value: s, label: s }));
