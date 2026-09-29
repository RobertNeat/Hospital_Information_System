import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { Select } from 'primeng/select';
import { MultiSelect } from 'primeng/multiselect';
import { Textarea } from 'primeng/textarea';
import { InputText } from 'primeng/inputtext';
import { Button } from 'primeng/button';
import { NOTE_CATEGORY_OPTIONS } from '../../constants/labels';
import type { ClinicalNote, ID, NoteCategory } from '../../models';
import { FormField } from '../form-field/form-field';

/** Common symptom picks for the "Objawy" MultiSelect (chips). */
const SYMPTOM_OPTIONS = [
  'Gorączka',
  'Ból głowy',
  'Ból w klatce piersiowej',
  'Duszność',
  'Kaszel',
  'Nudności',
  'Wymioty',
  'Ból brzucha',
  'Osłabienie',
  'Zawroty głowy',
  'Obrzęki',
  'Wysypka',
].map((label) => ({ label, value: label }));

export interface ClinicalNoteDialogSave {
  draft: Omit<ClinicalNote, 'id' | 'createdAt'>;
}

/** `p-dialog` form to create a new `ClinicalNote` for the patient's history. */
@Component({
  selector: 'app-clinical-note-dialog',
  imports: [
    Dialog,
    Select,
    MultiSelect,
    Textarea,
    InputText,
    Button,
    ReactiveFormsModule,
    FormField,
  ],
  templateUrl: './clinical-note-dialog.html',
  styleUrl: './clinical-note-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'clinical-note-dialog' },
})
export class ClinicalNoteDialog {
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly visible = input.required<boolean>();
  readonly patientId = input.required<ID>();
  readonly authorId = input.required<ID>();

  readonly visibleChange = output<boolean>();
  readonly save = output<ClinicalNoteDialogSave>();

  protected readonly categoryOptions = NOTE_CATEGORY_OPTIONS;
  protected readonly symptomOptions = SYMPTOM_OPTIONS;

  protected readonly form = this.fb.group({
    category: this.fb.control<NoteCategory>('progress', Validators.required),
    title: this.fb.control('', [Validators.required]),
    content: this.fb.control('', [Validators.required, Validators.minLength(10)]),
    symptoms: this.fb.control<string[]>([]),
  });

  protected onHide(): void {
    this.form.reset({ category: 'progress', title: '', content: '', symptoms: [] });
    this.visibleChange.emit(false);
  }

  protected onCancel(): void {
    this.form.reset({ category: 'progress', title: '', content: '', symptoms: [] });
    this.visibleChange.emit(false);
  }

  protected onSave(): void {
    this.form.markAllAsTouched();
    this.form.updateValueAndValidity();
    if (this.form.invalid) return;

    const value = this.form.getRawValue();
    this.save.emit({
      draft: {
        patientId: this.patientId(),
        authorId: this.authorId(),
        category: value.category,
        title: value.title,
        content: value.content,
        symptoms: value.symptoms.length ? value.symptoms : undefined,
      },
    });
    this.form.reset({ category: 'progress', title: '', content: '', symptoms: [] });
    this.visibleChange.emit(false);
  }
}
