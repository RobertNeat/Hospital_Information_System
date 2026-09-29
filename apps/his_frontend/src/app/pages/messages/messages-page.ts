import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService as ToastService } from 'primeng/api';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { MultiSelect } from 'primeng/multiselect';
import { SelectButton } from 'primeng/selectbutton';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { Tag } from 'primeng/tag';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import { DatePipe } from '@angular/common';

import { PageHeader } from '../../components/page-header/page-header';
import { EmptyState } from '../../components/empty-state/empty-state';
import { LoadingBlock } from '../../components/loading-block/loading-block';
import { DataTable } from '../../components/data-table/data-table';
import { StatusTag } from '../../components/status-tag/status-tag';
import { FormField } from '../../components/form-field/form-field';
import { PatientSearch } from '../../components/patient-search/patient-search';
import { MessageThreadList } from '../../components/message-thread-list/message-thread-list';
import { MessageConversation } from '../../components/message-conversation/message-conversation';
import { TaskDialog, type TaskDialogResult } from '../../components/task-dialog/task-dialog';
import {
  HandoffNoteDialog,
  type HandoffNoteDialogResult,
} from '../../components/handoff-note-dialog/handoff-note-dialog';

import { TeamMessageService } from '../../services/team-message.service';
import { StaffService } from '../../services/staff.service';
import { WardService } from '../../services/ward.service';
import { PatientService } from '../../services/patient.service';
import { StaffNamePipe } from '../../pipes/staff-name.pipe';
import { minutesAgo } from '../../utils/date-utils';
import type {
  ClinicalAlert,
  HandoffNote,
  Message as TeamMessage,
  MessageThread,
  PatientSummary,
  StaffMember,
  TableColumn,
  TeamTask,
  Ward,
} from '../../models';

type MessagesTab = 'inbox' | 'tasks' | 'handoff' | 'alerts';
type TaskFilter = 'assignedToMe' | 'createdByMe' | 'all';

interface TaskRow extends Record<string, unknown> {
  id: string;
  title: string;
  status: TeamTask['status'];
  priority: TeamTask['priority'];
  dueAt?: string;
  patientId?: string;
  patientLabel: string;
  overdue: boolean;
}

const TAB_VALUES: Record<MessagesTab, string> = {
  inbox: 'inbox',
  tasks: 'tasks',
  handoff: 'handoff',
  alerts: 'alerts',
};

@Component({
  selector: 'app-messages-page',
  imports: [
    ReactiveFormsModule,
    FormsModule,
    RouterLink,
    DatePipe,
    PageHeader,
    EmptyState,
    LoadingBlock,
    DataTable,
    StatusTag,
    FormField,
    PatientSearch,
    MessageThreadList,
    MessageConversation,
    TaskDialog,
    HandoffNoteDialog,
    Button,
    Dialog,
    InputText,
    Message,
    MultiSelect,
    SelectButton,
    Tab,
    TabList,
    TabPanel,
    TabPanels,
    Tabs,
    Tag,
    Textarea,
    Tooltip,
    StaffNamePipe,
  ],
  templateUrl: './messages-page.html',
  styleUrl: './messages-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'messages-page' },
})
export class MessagesPage {
  private readonly teamMessageService = inject(TeamMessageService);
  private readonly staffService = inject(StaffService);
  private readonly wardService = inject(WardService);
  private readonly patientService = inject(PatientService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly tab = input<MessagesTab>('inbox');
  readonly thread = input<string>();
  readonly patientId = input<string>();

  protected readonly currentUser = this.staffService.currentUser;

  // ---- shared reference data ----
  protected readonly messagingStaff = signal<StaffMember[]>([]);
  protected readonly wards = signal<Ward[]>([]);
  private readonly patientCache = new Map<string, PatientSummary>();
  protected readonly patientCacheTick = signal(0);

  constructor() {
    this.staffService.getStaff('doctor').subscribe((doctors) => {
      this.staffService.getStaff('nurse').subscribe((nurses) => {
        this.messagingStaff.set([...doctors, ...nurses]);
      });
    });
    this.wardService.getWards().subscribe((wards) => this.wards.set(wards));
    this.loadThreads();
    this.loadTasks();
    this.loadHandoffNotes();
    this.loadAlerts();

    const initialPatientId = this.patientId();
    if (initialPatientId) {
      this.resolvePatient(initialPatientId);
    }
  }

  protected readonly activeTab = computed(() => TAB_VALUES[this.tab()]);

  protected onTabChange(value: string | number | undefined): void {
    if (value === undefined) return;
    this.router.navigate([], { queryParams: { tab: value }, queryParamsHandling: 'merge' });
  }

  protected patientLabel = (patientId: string): string => {
    const cached = this.patientCache.get(patientId);
    return cached ? `${cached.lastName} ${cached.firstName}` : patientId;
  };

  private resolvePatient(id: string): void {
    if (this.patientCache.has(id)) return;
    this.patientService.getPatientById(id).subscribe({
      next: (p) => {
        this.patientCache.set(id, {
          id: p.id,
          mrn: p.mrn,
          pesel: p.pesel,
          firstName: p.firstName,
          lastName: p.lastName,
          birthDate: p.birthDate,
          gender: p.gender,
          status: p.status,
          flags: p.flags,
        });
        this.patientCacheTick.update((n) => n + 1);
      },
      error: () => undefined,
    });
  }

  // ---- Wiadomości ----
  protected readonly threads = signal<MessageThread[]>([]);
  protected readonly threadsLoading = signal(true);
  protected readonly selectedThreadId = signal<string | null>(null);
  protected readonly selectedThreadMessages = signal<TeamMessage[]>([]);
  protected readonly conversationSending = signal(false);

  protected readonly selectedThread = computed(
    () => this.threads().find((t) => t.id === this.selectedThreadId()) ?? null,
  );

  protected readonly newMessageDialogVisible = signal(false);

  protected readonly newMessageForm = this.fb.group({
    recipientIds: this.fb.control<string[]>([], [Validators.required]),
    subject: this.fb.control('', [Validators.required]),
    body: this.fb.control('', [Validators.required]),
  });
  protected readonly newMessagePatient = signal<PatientSummary | null>(null);

  private loadThreads(): void {
    this.threadsLoading.set(true);
    this.teamMessageService.getThreads(this.currentUser().id).subscribe((threads) => {
      this.threads.set(threads);
      this.threadsLoading.set(false);
      for (const t of threads) {
        if (t.patientId) this.resolvePatient(t.patientId);
      }
      const preselect = this.thread() ?? threads[0]?.id ?? null;
      if (preselect) this.selectThread(preselect);
    });
  }

  protected selectThread(threadId: string): void {
    this.selectedThreadId.set(threadId);
    this.teamMessageService.getMessages(threadId).subscribe((messages) => {
      this.selectedThreadMessages.set(messages);
    });
    this.teamMessageService.markThreadRead(threadId, this.currentUser().id).subscribe((updated) => {
      this.threads.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
    });
  }

  protected onThreadSelected(t: MessageThread): void {
    this.router.navigate([], {
      queryParams: { tab: 'inbox', thread: t.id },
      queryParamsHandling: 'merge',
    });
    this.selectThread(t.id);
  }

  protected staffLabel(staffId: string): string {
    return this.staffService.nameOf(staffId);
  }

  protected sendMessage(event: { body: string; priority: 'normal' | 'high' | 'critical' }): void {
    const threadId = this.selectedThreadId();
    if (!threadId) return;
    this.conversationSending.set(true);
    this.teamMessageService.sendMessage(threadId, event.body, event.priority).subscribe({
      next: () => {
        this.conversationSending.set(false);
        this.teamMessageService.getMessages(threadId).subscribe((messages) => {
          this.selectedThreadMessages.set(messages);
        });
        this.toast.add({ severity: 'success', summary: 'Wysłano wiadomość' });
      },
      error: () => {
        this.conversationSending.set(false);
        this.toast.add({ severity: 'error', summary: 'Nie udało się wysłać wiadomości' });
      },
    });
  }

  protected openNewMessageDialog(): void {
    this.newMessageForm.reset({ recipientIds: [], subject: '', body: '' });
    this.newMessagePatient.set(null);
    this.newMessageDialogVisible.set(true);
  }

  protected onNewMessagePatientSelected(patient: PatientSummary): void {
    this.newMessagePatient.set(patient);
    this.patientCache.set(patient.id, patient);
  }

  protected submitNewMessage(): void {
    this.newMessageForm.markAllAsTouched();
    if (this.newMessageForm.invalid) return;
    const value = this.newMessageForm.getRawValue();
    const participantIds = [this.currentUser().id, ...value.recipientIds];
    this.teamMessageService
      .createThread(participantIds, value.subject, this.newMessagePatient()?.id, value.body)
      .subscribe({
        next: (thread) => {
          this.toast.add({ severity: 'success', summary: 'Utworzono wątek' });
          this.newMessageDialogVisible.set(false);
          this.loadThreads();
          this.router.navigate([], {
            queryParams: { tab: 'inbox', thread: thread.id },
            queryParamsHandling: 'merge',
          });
        },
        error: () => this.toast.add({ severity: 'error', summary: 'Nie udało się utworzyć wątku' }),
      });
  }

  // ---- Zadania ----
  protected readonly tasks = signal<TeamTask[]>([]);
  protected readonly tasksLoading = signal(true);
  protected readonly taskFilter = signal<TaskFilter>('assignedToMe');
  protected readonly taskDialogVisible = signal(false);

  protected readonly taskFilterOptions: { label: string; value: TaskFilter }[] = [
    { label: 'Przydzielone mnie', value: 'assignedToMe' },
    { label: 'Zlecone przeze mnie', value: 'createdByMe' },
    { label: 'Wszystkie', value: 'all' },
  ];

  protected readonly taskColumns: TableColumn<TaskRow>[] = [
    { field: 'title', header: 'Zadanie', sortable: true },
    { field: 'patientLabel', header: 'Pacjent' },
    { field: 'priority', header: 'Priorytet', type: 'tag', tagKind: 'priority' },
    { field: 'status', header: 'Status', type: 'tag', tagKind: 'taskStatus' },
    { field: 'dueAt', header: 'Termin', type: 'datetime', sortable: true },
  ];

  protected readonly taskRows = computed<TaskRow[]>(() => {
    this.patientCacheTick();
    const filter = this.taskFilter();
    const me = this.currentUser().id;
    const now = Date.now();
    return this.tasks()
      .filter((t) => {
        if (filter === 'assignedToMe') return t.assignedToId === me;
        if (filter === 'createdByMe') return t.createdById === me;
        return true;
      })
      .map((t) => ({
        id: t.id,
        title: t.title,
        status: t.status,
        priority: t.priority,
        dueAt: t.dueAt,
        patientId: t.patientId,
        patientLabel: t.patientId ? this.patientLabel(t.patientId) : '—',
        overdue: !!t.dueAt && new Date(t.dueAt).getTime() < now && t.status !== 'done',
      }));
  });

  private loadTasks(): void {
    this.tasksLoading.set(true);
    this.teamMessageService.getTasks().subscribe((tasks) => {
      this.tasks.set(tasks);
      this.tasksLoading.set(false);
      for (const t of tasks) {
        if (t.patientId) this.resolvePatient(t.patientId);
      }
    });
  }

  protected startTask(row: TaskRow): void {
    this.teamMessageService.updateTaskStatus(row.id, 'in_progress').subscribe({
      next: () => {
        this.loadTasks();
        this.toast.add({ severity: 'info', summary: 'Zadanie rozpoczęte' });
      },
      error: () =>
        this.toast.add({ severity: 'error', summary: 'Nie udało się zaktualizować zadania' }),
    });
  }

  protected finishTask(row: TaskRow): void {
    this.teamMessageService.updateTaskStatus(row.id, 'done').subscribe({
      next: () => {
        this.loadTasks();
        this.toast.add({ severity: 'success', summary: 'Zadanie zakończone' });
      },
      error: () =>
        this.toast.add({ severity: 'error', summary: 'Nie udało się zaktualizować zadania' }),
    });
  }

  protected readonly taskDialogInitialPatient = computed<PatientSummary | null>(() => {
    const id = this.patientId();
    if (!id) return null;
    return this.patientCache.get(id) ?? null;
  });

  protected onCreateTask(result: TaskDialogResult): void {
    this.teamMessageService
      .createTask({
        title: result.title,
        description: result.description,
        patientId: result.patientId,
        assignedToId: result.assignedToId,
        createdById: this.currentUser().id,
        dueAt: result.dueAt,
        priority: result.priority,
        status: 'open',
      })
      .subscribe({
        next: () => {
          this.toast.add({ severity: 'success', summary: 'Zadanie zlecone' });
          this.loadTasks();
        },
        error: () => this.toast.add({ severity: 'error', summary: 'Nie udało się zlecić zadania' }),
      });
  }

  // ---- Przekazanie dyżuru ----
  protected readonly handoffNotes = signal<HandoffNote[]>([]);
  protected readonly handoffLoading = signal(true);
  protected readonly handoffDialogVisible = signal(false);

  private loadHandoffNotes(): void {
    this.handoffLoading.set(true);
    this.teamMessageService.getHandoffNotes().subscribe((notes) => {
      this.handoffNotes.set(notes);
      this.handoffLoading.set(false);
      for (const n of notes) {
        for (const p of n.patientNotes) this.resolvePatient(p.patientId);
      }
    });
  }

  protected wardName(wardId: string): string {
    return this.wardService.nameOf(wardId);
  }

  protected onCreateHandoffNote(result: HandoffNoteDialogResult): void {
    this.teamMessageService
      .createHandoffNote({
        wardId: result.wardId,
        shiftDate: new Date().toISOString().slice(0, 10),
        shift: result.shift,
        fromId: this.currentUser().id,
        toId: result.toId,
        generalNotes: result.generalNotes,
        patientNotes: result.patientNotes,
      })
      .subscribe({
        next: () => {
          this.toast.add({ severity: 'success', summary: 'Przekazanie dyżuru zapisane' });
          this.loadHandoffNotes();
        },
        error: () =>
          this.toast.add({ severity: 'error', summary: 'Nie udało się zapisać przekazania' }),
      });
  }

  // ---- Alerty ----
  protected readonly alerts = signal<ClinicalAlert[]>([]);
  protected readonly alertsLoading = signal(true);

  protected readonly sortedAlerts = computed(() => {
    const order: Record<ClinicalAlert['severity'], number> = { critical: 0, warning: 1, info: 2 };
    return [...this.alerts()].sort((a, b) => order[a.severity] - order[b.severity]);
  });

  private loadAlerts(): void {
    this.alertsLoading.set(true);
    this.teamMessageService.getAlerts().subscribe((alerts) => {
      this.alerts.set(alerts);
      this.alertsLoading.set(false);
      for (const a of alerts) {
        if (a.patientId) this.resolvePatient(a.patientId);
      }
    });
  }

  protected acknowledgeAlert(alert: ClinicalAlert): void {
    this.teamMessageService.acknowledgeAlert(alert.id, this.currentUser().id).subscribe({
      next: () => {
        this.loadAlerts();
        this.toast.add({ severity: 'success', summary: 'Alert potwierdzony' });
      },
      error: () =>
        this.toast.add({ severity: 'error', summary: 'Nie udało się potwierdzić alertu' }),
    });
  }

  protected alertAgo(alert: ClinicalAlert): string {
    const mins = minutesAgo(alert.createdAt);
    if (mins < 60) return `${mins} min temu`;
    const hours = Math.round(mins / 60);
    return `${hours} godz. temu`;
  }
}
