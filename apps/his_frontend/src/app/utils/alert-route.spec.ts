import { describe, expect, it } from 'vitest';
import { alertRoute } from './alert-route';

describe('alertRoute', () => {
  it('returns null without a target', () => {
    expect(alertRoute({})).toBeNull();
  });

  it('routes result targets through the patient', () => {
    expect(
      alertRoute({ target: { kind: 'lab_result', id: 'r1', patientId: 'p1' } })?.commands,
    ).toEqual(['/patients', 'p1', 'results', 'lab', 'r1']);
    expect(
      alertRoute({ target: { kind: 'imaging_result', id: 'r2' }, patientId: 'p2' })?.commands,
    ).toEqual(['/patients', 'p2', 'results', 'imaging', 'r2']);
  });

  it('uses the target id as patient id for vitals', () => {
    expect(alertRoute({ target: { kind: 'patient_vitals', id: 'p3' } })?.commands).toEqual([
      '/patients',
      'p3',
      'vitals',
    ]);
  });

  it('routes orders to the patient orders and tasks to the tasks tab', () => {
    expect(
      alertRoute({ target: { kind: 'lab_order', id: 'o1', patientId: 'p1' } })?.commands,
    ).toEqual(['/patients', 'p1', 'orders']);
    expect(alertRoute({ target: { kind: 'task', id: 't1' } })).toEqual({
      commands: ['/messages'],
      queryParams: { tab: 'tasks' },
    });
  });
});
