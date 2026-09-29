import type { TagSeverity } from '../models';

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
  | 'alertSeverity';

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

export const ORDER_STATUS_SEVERITY: Record<
  'ordered' | 'scheduled' | 'specimen_collected' | 'in_progress' | 'completed' | 'cancelled',
  TagSeverity
> = {
  ordered: 'info',
  scheduled: 'info',
  specimen_collected: 'warn',
  in_progress: 'warn',
  completed: 'success',
  cancelled: 'secondary',
};

export const URGENCY_SEVERITY: Record<'routine' | 'urgent' | 'stat', TagSeverity> = {
  routine: 'secondary',
  urgent: 'warn',
  stat: 'danger',
};

export const RESULT_FLAG_SEVERITY: Record<'N' | 'L' | 'H' | 'LL' | 'HH' | 'A', TagSeverity> = {
  N: 'success',
  L: 'warn',
  H: 'warn',
  LL: 'danger',
  HH: 'danger',
  A: 'danger',
};

export const RESULT_STATUS_SEVERITY: Record<'preliminary' | 'final' | 'corrected', TagSeverity> = {
  preliminary: 'info',
  final: 'success',
  corrected: 'warn',
};

export const PRESCRIPTION_STATUS_SEVERITY: Record<
  'issued' | 'partially_dispensed' | 'dispensed' | 'cancelled' | 'expired',
  TagSeverity
> = {
  issued: 'info',
  partially_dispensed: 'warn',
  dispensed: 'success',
  cancelled: 'secondary',
  expired: 'danger',
};

export const PRIORITY_SEVERITY: Record<'normal' | 'high' | 'critical', TagSeverity> = {
  normal: 'secondary',
  high: 'warn',
  critical: 'danger',
};

export const TASK_STATUS_SEVERITY: Record<
  'open' | 'in_progress' | 'done' | 'cancelled',
  TagSeverity
> = {
  open: 'info',
  in_progress: 'warn',
  done: 'success',
  cancelled: 'secondary',
};

export const TRIAGE_SEVERITY: Record<'red' | 'orange' | 'yellow' | 'green' | 'blue', TagSeverity> =
  {
    red: 'danger',
    orange: 'warn',
    yellow: 'warn',
    green: 'success',
    blue: 'info',
  };

export const ADMISSION_STATUS_SEVERITY: Record<
  'registered' | 'admitted' | 'outpatient' | 'discharged',
  TagSeverity
> = {
  registered: 'info',
  admitted: 'warn',
  outpatient: 'secondary',
  discharged: 'success',
};

export const ALLERGY_SEVERITY_SEVERITY: Record<
  'mild' | 'moderate' | 'severe' | 'life_threatening',
  TagSeverity
> = {
  mild: 'info',
  moderate: 'warn',
  severe: 'danger',
  life_threatening: 'danger',
};

export const ALERT_SEVERITY_SEVERITY: Record<'info' | 'warning' | 'critical', TagSeverity> = {
  info: 'info',
  warning: 'warn',
  critical: 'danger',
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
};
