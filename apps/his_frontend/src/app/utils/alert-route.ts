import type { ClinicalAlert } from '../models';

export interface AlertRoute {
  commands: string[];
  queryParams?: Record<string, string>;
}

/** Maps `ClinicalAlert.target` to an app route (the backend does not know UI routes). */
export function alertRoute(alert: Pick<ClinicalAlert, 'target' | 'patientId'>): AlertRoute | null {
  const target = alert.target;
  if (!target) return null;
  const patientId = target.patientId ?? alert.patientId;
  switch (target.kind) {
    case 'lab_result':
      return patientId
        ? { commands: ['/patients', patientId, 'results', 'lab', target.id] }
        : { commands: ['/results'] };
    case 'imaging_result':
      return patientId
        ? { commands: ['/patients', patientId, 'results', 'imaging', target.id] }
        : { commands: ['/results'] };
    case 'patient_vitals':
      return { commands: ['/patients', target.patientId ?? target.id, 'vitals'] };
    case 'lab_order':
    case 'imaging_order':
      return patientId
        ? { commands: ['/patients', patientId, 'orders'] }
        : { commands: ['/orders'] };
    case 'task':
      return { commands: ['/messages'], queryParams: { tab: 'tasks' } };
    case 'patient':
      return { commands: ['/patients', target.id, 'overview'] };
  }
}
