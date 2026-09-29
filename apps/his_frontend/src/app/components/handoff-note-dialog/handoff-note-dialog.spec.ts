import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HandoffNoteDialog, type HandoffNoteDialogResult } from './handoff-note-dialog';
import type { StaffMember, Ward } from '../../models';

const WARDS: Ward[] = [
  { id: 'ward-int', name: 'Oddział Chorób Wewnętrznych', shortName: 'INT', floor: '1', beds: 20 },
];

const STAFF: StaffMember[] = [
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

describe('HandoffNoteDialog', () => {
  it('renders when visible', async () => {
    const fixture = TestBed.createComponent(HandoffNoteDialog);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('wards', WARDS);
    fixture.componentRef.setInput('recipients', STAFF);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Nowe przekazanie dyżuru');
  });

  it('adds and removes a patient SBAR entry', async () => {
    const fixture = TestBed.createComponent(HandoffNoteDialog);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('wards', WARDS);
    fixture.componentRef.setInput('recipients', STAFF);
    await fixture.whenStable();

    const instance = fixture.componentInstance as unknown as {
      addPatientEntry: () => void;
      removePatientEntry: (i: number) => void;
      patientNotesArray: { length: number };
    };
    instance.addPatientEntry();
    expect(instance.patientNotesArray.length).toBe(1);
    instance.removePatientEntry(0);
    expect(instance.patientNotesArray.length).toBe(0);
  });

  it('does not emit create when no patient entry was added', () => {
    const fixture = TestBed.createComponent(HandoffNoteDialog);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('wards', WARDS);
    fixture.componentRef.setInput('recipients', STAFF);

    let emitted: HandoffNoteDialogResult | undefined;
    fixture.componentInstance.create.subscribe((e) => (emitted = e));
    (fixture.componentInstance as unknown as { submit: () => void }).submit();

    expect(emitted).toBeUndefined();
  });
});
