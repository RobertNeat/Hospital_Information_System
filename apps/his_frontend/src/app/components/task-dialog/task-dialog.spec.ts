import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { TaskDialog, type TaskDialogResult } from './task-dialog';
import type { StaffMember } from '../../models';

const STAFF: StaffMember[] = [
  {
    id: 'stf-002',
    title: 'dr n. med.',
    firstName: 'Piotr',
    lastName: 'Wiśniewski',
    role: 'doctor',
    wardId: 'ward-kar',
    online: true,
  },
  {
    id: 'stf-006',
    title: 'mgr piel.',
    firstName: 'Katarzyna',
    lastName: 'Zielińska',
    role: 'nurse',
    wardId: 'ward-int',
    online: false,
  },
];

describe('TaskDialog', () => {
  it('renders when visible', async () => {
    const fixture = TestBed.createComponent(TaskDialog);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('staff', STAFF);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Deleguj zadanie');
  });

  it('groups staff options by role', async () => {
    const fixture = TestBed.createComponent(TaskDialog);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('staff', STAFF);
    await fixture.whenStable();
    const groups = (
      fixture.componentInstance as unknown as {
        staffGroups: () => { label: string; items: unknown[] }[];
      }
    ).staffGroups();
    expect(groups.map((g) => g.label)).toEqual(['Lekarz', 'Pielęgniarka/Pielęgniarz']);
    expect(groups[0].items).toHaveLength(1);
  });

  it('does not emit create when the form is invalid', () => {
    const fixture = TestBed.createComponent(TaskDialog);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('staff', STAFF);

    let emitted: TaskDialogResult | undefined;
    fixture.componentInstance.create.subscribe((e) => (emitted = e));
    (fixture.componentInstance as unknown as { submit: () => void }).submit();

    expect(emitted).toBeUndefined();
  });
});
