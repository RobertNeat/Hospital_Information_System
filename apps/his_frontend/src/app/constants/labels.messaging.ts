import type { StaffAccountStatus, StaffRole } from '../models';
import { toOptions } from './labels.utils';

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

export const STAFF_ROLE_LABELS: Record<StaffRole, string> = {
  doctor: 'Lekarz',
  nurse: 'Pielęgniarka/Pielęgniarz',
  lab_technician: 'Diagnosta laboratoryjny',
  radiologist: 'Radiolog',
  pharmacist: 'Farmaceuta',
  registrar: 'Rejestrator/Rejestratorka',
  admin: 'Administrator',
};
/** Self-registration and messaging pickers stay limited to clinical roles (doctor, nurse). */
export const STAFF_ROLE_OPTIONS = toOptions(STAFF_ROLE_LABELS).filter(
  (o) => o.value === 'doctor' || o.value === 'nurse',
);

export const ACCOUNT_STATUS_LABELS: Record<StaffAccountStatus, string> = {
  pending: 'Oczekujące',
  active: 'Aktywne',
  locked: 'Zablokowane',
};
