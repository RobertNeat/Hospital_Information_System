import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePicker } from 'primeng/datepicker';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Button } from 'primeng/button';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import { PRIORITY_OPTIONS, STAFF_ROLE_LABELS } from '../../constants/labels';
import { FormField } from '../form-field/form-field';
import { PatientSearch } from '../patient-search/patient-search';
import type { ID, Priority, StaffMember, StaffRole, PatientSummary } from '../../models';

export interface TaskDialogResult {
  title: string;
  description?: string;
  assignedToId: ID;
  patientId?: ID;
  priority: Priority;
  dueAt?: string;
}

interface StaffOptionGroup {
  label: string;
  items: { label: string; value: ID }[];
}

/** "Deleguj zadanie" dialog: title, description, assignee, patient, priority, due date. */
@Component({
  selector: 'app-task-dialog',
  imports: [
    ReactiveFormsModule,
    Dialog,
    InputText,
    Select,
    DatePicker,
    Button,
    Textarea,
    Tooltip,
    FormField,
    PatientSearch,
  ],
  templateUrl: './task-dialog.html',
  styleUrl: './task-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'task-dialog' },
})
export class TaskDialog {
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly visible = input.required<boolean>();
  readonly staff = input.required<StaffMember[]>();
  readonly initialPatient = input<PatientSummary | null>(null);

  readonly visibleChange = output<boolean>();
  readonly create = output<TaskDialogResult>();

  protected readonly priorityOptions = PRIORITY_OPTIONS;
  protected readonly selectedPatient = signal<PatientSummary | null>(null);

  protected readonly staffGroups = computed<StaffOptionGroup[]>(() => {
    const roles: StaffRole[] = ['doctor', 'nurse'];
    return roles.map((role) => ({
      label: STAFF_ROLE_LABELS[role],
      items: this.staff()
        .filter((s) => s.role === role)
        .map((s) => ({ label: `${s.title} ${s.firstName} ${s.lastName}`, value: s.id })),
    }));
  });

  protected readonly form = this.fb.group({
    title: this.fb.control('', [Validators.required]),
    description: this.fb.control(''),
    assignedToId: this.fb.control('', [Validators.required]),
    priority: this.fb.control<Priority>('normal', [Validators.required]),
    dueAt: this.fb.control<Date | null>(null),
  });

  constructor() {
    const patient = this.initialPatient();
    if (patient) this.selectedPatient.set(patient);
  }

  protected onPatientSelected(patient: PatientSummary): void {
    this.selectedPatient.set(patient);
  }

  protected clearPatient(): void {
    this.selectedPatient.set(null);
  }

  protected onHide(): void {
    this.visibleChange.emit(false);
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.create.emit({
      title: value.title,
      description: value.description || undefined,
      assignedToId: value.assignedToId,
      patientId: this.selectedPatient()?.id,
      priority: value.priority,
      dueAt: value.dueAt ? value.dueAt.toISOString() : undefined,
    });
    this.form.reset({
      title: '',
      description: '',
      assignedToId: '',
      priority: 'normal',
      dueAt: null,
    });
    this.selectedPatient.set(null);
    this.visibleChange.emit(false);
  }
}
