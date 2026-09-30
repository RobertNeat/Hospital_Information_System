import type {
  AdmissionStatus,
  Allergy,
  ClinicalAlert,
  Diagnosis,
  Encounter,
  LabResult,
  OrderStatus,
  OrderUrgency,
  Priority,
  PrescriptionStatus,
  ResultFlag,
  TagSeverity,
  TaskStatus,
  TriageLevel,
} from '../models';

/**
 * The kinds of domain values `app-status-tag` (Phase 0b) knows how to render.
 * Declared here (rather than in `models/table.model.ts`) because it is a
 * presentation-layer concept, and `table.model.ts` imports it from this file.
 */
export type TagKind =
  | 'orderStatus'
  | 'urgency'
  | 'resultFlag'
  | 'resultStatus'
  | 'prescriptionStatus'
  | 'priority'
  | 'taskStatus'
  | 'triage'
  | 'admissionStatus'
  | 'allergySeverity'
  | 'alertSeverity'
  | 'diagnosisStatus'
  | 'encounterStatus'
  | 'allergyStatus';

/**
 * Semantic severity mapping (ADDENDUM B.1). PrimeNG's Aura preset maps its
 * `p-tag`/`p-message`/`p-button` `severity` inputs onto fixed primitive palettes:
 *   success -> green, info -> sky/blue, warn -> orange, danger -> red,
 *   secondary -> surface (gray), contrast -> inverted surface.
 * Every severity value below is one of PrimeNG's own `TagSeverity` values, so
 * `app-status-tag` and any component can pass it straight through to `severity`
 * and get a consistent, accessible color across the whole app -- never hardcode
 * hex colors for status meaning.
 */

export const ORDER_STATUS_SEVERITY: Record<OrderStatus, TagSeverity> = {
  ordered: 'info',
  scheduled: 'info',
  specimen_collected: 'warn',
  in_progress: 'warn',
  completed: 'success',
  cancelled: 'secondary',
};

export const URGENCY_SEVERITY: Record<OrderUrgency, TagSeverity> = {
  routine: 'secondary',
  urgent: 'warn',
  stat: 'danger',
};

export const RESULT_FLAG_SEVERITY: Record<ResultFlag, TagSeverity> = {
  N: 'success',
  L: 'warn',
  H: 'warn',
  LL: 'danger',
  HH: 'danger',
  A: 'danger',
};

export const RESULT_STATUS_SEVERITY: Record<LabResult['status'], TagSeverity> = {
  preliminary: 'info',
  final: 'success',
  corrected: 'warn',
};

export const PRESCRIPTION_STATUS_SEVERITY: Record<PrescriptionStatus, TagSeverity> = {
  issued: 'info',
  partially_dispensed: 'warn',
  dispensed: 'success',
  cancelled: 'secondary',
  expired: 'danger',
};

export const PRIORITY_SEVERITY: Record<Priority, TagSeverity> = {
  normal: 'secondary',
  high: 'warn',
  critical: 'danger',
};

export const TASK_STATUS_SEVERITY: Record<TaskStatus, TagSeverity> = {
  open: 'info',
  in_progress: 'warn',
  done: 'success',
  cancelled: 'secondary',
};

export const TRIAGE_SEVERITY: Record<TriageLevel, TagSeverity> = {
  red: 'danger',
  orange: 'warn',
  yellow: 'warn',
  green: 'success',
  blue: 'info',
};

export const ADMISSION_STATUS_SEVERITY: Record<AdmissionStatus, TagSeverity> = {
  registered: 'info',
  admitted: 'warn',
  outpatient: 'secondary',
  discharged: 'success',
};

export const ALLERGY_SEVERITY_SEVERITY: Record<Allergy['severity'], TagSeverity> = {
  mild: 'info',
  moderate: 'warn',
  severe: 'danger',
  life_threatening: 'danger',
};

export const ALERT_SEVERITY_SEVERITY: Record<ClinicalAlert['severity'], TagSeverity> = {
  info: 'info',
  warning: 'warn',
  critical: 'danger',
};

export const DIAGNOSIS_STATUS_SEVERITY: Record<Diagnosis['status'], TagSeverity> = {
  active: 'warn',
  resolved: 'success',
};

export const ENCOUNTER_STATUS_SEVERITY: Record<Encounter['status'], TagSeverity> = {
  planned: 'info',
  in_progress: 'warn',
  finished: 'success',
  cancelled: 'secondary',
};

// `resolved` is kept alongside the model's statuses so it stays aligned with ALLERGY_STATUS_LABELS.
export const ALLERGY_STATUS_SEVERITY: Record<Allergy['status'] | 'resolved', TagSeverity> = {
  active: 'danger',
  resolved: 'success',
  inactive: 'secondary',
};

/** Combined lookup keyed by `TagKind`, used by `app-status-tag`. */
export const TAG_SEVERITY_MAP: Record<TagKind, Record<string, TagSeverity>> = {
  orderStatus: ORDER_STATUS_SEVERITY,
  urgency: URGENCY_SEVERITY,
  resultFlag: RESULT_FLAG_SEVERITY,
  resultStatus: RESULT_STATUS_SEVERITY,
  prescriptionStatus: PRESCRIPTION_STATUS_SEVERITY,
  priority: PRIORITY_SEVERITY,
  taskStatus: TASK_STATUS_SEVERITY,
  triage: TRIAGE_SEVERITY,
  admissionStatus: ADMISSION_STATUS_SEVERITY,
  allergySeverity: ALLERGY_SEVERITY_SEVERITY,
  alertSeverity: ALERT_SEVERITY_SEVERITY,
  diagnosisStatus: DIAGNOSIS_STATUS_SEVERITY,
  encounterStatus: ENCOUNTER_STATUS_SEVERITY,
  allergyStatus: ALLERGY_STATUS_SEVERITY,
};
