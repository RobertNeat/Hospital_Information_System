import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Select } from 'primeng/select';
import { SelectButton } from 'primeng/selectbutton';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import { SHIFT_OPTIONS } from '../../constants/labels';
import { FormField } from '../form-field/form-field';
import { PatientSearch } from '../patient-search/patient-search';
import type {
  HandoffPatientNote,
  ID,
  PatientSummary,
  ShiftType,
  StaffMember,
  Ward,
} from '../../models';

export interface HandoffNoteDialogResult {
  wardId: ID;
  shift: ShiftType;
  toId: ID;
  generalNotes?: string;
  patientNotes: HandoffPatientNote[];
}

/** "Nowe przekazanie" dialog: ward/shift/recipient + FormArray of per-patient SBAR entries. */
@Component({
  selector: 'app-handoff-note-dialog',
  imports: [
    ReactiveFormsModule,
    Dialog,
    Select,
    SelectButton,
    Textarea,
    Tooltip,
    Button,
    FormField,
    PatientSearch,
  ],
  templateUrl: './handoff-note-dialog.html',
  styleUrl: './handoff-note-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'handoff-note-dialog' },
})
export class HandoffNoteDialog {
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly visible = input.required<boolean>();
  readonly wards = input.required<Ward[]>();
  readonly recipients = input.required<StaffMember[]>();

  readonly visibleChange = output<boolean>();
  readonly create = output<HandoffNoteDialogResult>();

  protected readonly shiftOptions = SHIFT_OPTIONS;

  /** Selected patients keyed by FormArray index -- p-autocomplete's picked value can't live in the typed form. */
  protected readonly patientEntries = signal<(PatientSummary | null)[]>([]);

  protected readonly form = this.fb.group({
    wardId: this.fb.control('', [Validators.required]),
    shift: this.fb.control<'day' | 'night'>('day', [Validators.required]),
    toId: this.fb.control('', [Validators.required]),
    generalNotes: this.fb.control(''),
    patientNotes: this.fb.array<ReturnType<typeof this.buildPatientNoteGroup>>([]),
  });

  protected get patientNotesArray() {
    return this.form.controls.patientNotes;
  }

  private buildPatientNoteGroup() {
    return this.fb.group({
      situation: this.fb.control('', [Validators.required]),
      background: this.fb.control('', [Validators.required]),
      assessment: this.fb.control('', [Validators.required]),
      recommendation: this.fb.control('', [Validators.required]),
    });
  }

  protected addPatientEntry(): void {
    this.patientNotesArray.push(this.buildPatientNoteGroup());
    this.patientEntries.update((list) => [...list, null]);
  }

  protected removePatientEntry(index: number): void {
    this.patientNotesArray.removeAt(index);
    this.patientEntries.update((list) => list.filter((_, i) => i !== index));
  }

  protected onPatientSelected(index: number, patient: PatientSummary): void {
    this.patientEntries.update((list) => list.map((p, i) => (i === index ? patient : p)));
  }

  protected onHide(): void {
    this.visibleChange.emit(false);
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    this.patientNotesArray.controls.forEach((c) => c.markAllAsTouched());
    if (this.form.invalid) return;
    if (this.patientNotesArray.length === 0) return;
    if (this.patientEntries().some((p) => !p)) return;

    const value = this.form.getRawValue();
    const patientNotes: HandoffPatientNote[] = value.patientNotes.map((note, index) => ({
      patientId: this.patientEntries()[index]!.id,
      situation: note.situation,
      background: note.background,
      assessment: note.assessment,
      recommendation: note.recommendation,
    }));

    this.create.emit({
      wardId: value.wardId,
      shift: value.shift,
      toId: value.toId,
      generalNotes: value.generalNotes || undefined,
      patientNotes,
    });

    this.resetForm();
    this.visibleChange.emit(false);
  }

  private resetForm(): void {
    this.form.reset({ wardId: '', shift: 'day', toId: '', generalNotes: '' });
    this.patientNotesArray.clear();
    this.patientEntries.set([]);
  }
}
