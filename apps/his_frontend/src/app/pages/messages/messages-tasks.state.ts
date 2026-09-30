import { computed, inject, signal, type Signal } from '@angular/core';
import type { TaskDialogResult } from '../../components/task-dialog/task-dialog';
import type { PatientSummary, TableColumn, TeamTask } from '../../models';
import { TeamMessageService } from '../../services/team-message.service';
import type { MessagesContext } from './messages.context';

export type TaskFilter = 'assignedToMe' | 'createdByMe' | 'all';

export interface TaskRow extends Record<string, unknown> {
  id: string;
  title: string;
  status: TeamTask['status'];
  priority: TeamTask['priority'];
  dueAt?: string;
  patientId?: string;
  patientLabel: string;
  overdue: boolean;
}

const TASK_FILTER_OPTIONS: { label: string; value: TaskFilter }[] = [
  { label: 'Przydzielone mnie', value: 'assignedToMe' },
  { label: 'Zlecone przeze mnie', value: 'createdByMe' },
  { label: 'Wszystkie', value: 'all' },
];

const TASK_COLUMNS: TableColumn<TaskRow>[] = [
  { field: 'title', header: 'Zadanie', sortable: true },
  { field: 'patientLabel', header: 'Pacjent' },
  { field: 'priority', header: 'Priorytet', type: 'tag', tagKind: 'priority' },
  { field: 'status', header: 'Status', type: 'tag', tagKind: 'taskStatus' },
  { field: 'dueAt', header: 'Termin', type: 'datetime', sortable: true },
];

/** "Zadania" tab. Injection context only. */
export function createTasksState(
  ctx: MessagesContext,
  initialPatientId: Signal<string | undefined>,
) {
  const service = inject(TeamMessageService);
  const { toast, patients } = ctx;

  const tasks = signal<TeamTask[]>([]);
  const tasksLoading = signal(true);
  const taskFilter = signal<TaskFilter>('assignedToMe');
  const taskDialogVisible = signal(false);

  const taskRows = computed<TaskRow[]>(() => {
    patients.tick();
    const filter = taskFilter();
    const me = ctx.currentUser().id;
    const now = Date.now();
    return tasks()
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
        patientLabel: t.patientId ? patients.label(t.patientId) : '—',
        overdue: !!t.dueAt && new Date(t.dueAt).getTime() < now && t.status !== 'done',
      }));
  });

  const load = (): void => {
    tasksLoading.set(true);
    service.getTasks().subscribe((list) => {
      tasks.set(list);
      tasksLoading.set(false);
      for (const t of list) {
        if (t.patientId) patients.resolve(t.patientId);
      }
    });
  };

  const updateStatus = (row: TaskRow, status: 'in_progress' | 'done', summary: string): void => {
    service.updateTaskStatus(row.id, status).subscribe({
      next: () => {
        load();
        toast.add({ severity: status === 'done' ? 'success' : 'info', summary });
      },
      error: () => toast.add({ severity: 'error', summary: 'Nie udało się zaktualizować zadania' }),
    });
  };

  return {
    taskFilterOptions: TASK_FILTER_OPTIONS,
    taskColumns: TASK_COLUMNS,
    taskFilter,
    taskDialogVisible,
    tasksLoading,
    taskRows,
    load,
    startTask: (row: TaskRow): void => updateStatus(row, 'in_progress', 'Zadanie rozpoczęte'),
    finishTask: (row: TaskRow): void => updateStatus(row, 'done', 'Zadanie zakończone'),
    taskDialogInitialPatient: computed<PatientSummary | null>(() => {
      const id = initialPatientId();
      return id ? (patients.cache.get(id) ?? null) : null;
    }),

    onCreateTask: (result: TaskDialogResult): void => {
      service
        .createTask({
          title: result.title,
          description: result.description,
          patientId: result.patientId,
          assignedToId: result.assignedToId,
          createdById: ctx.currentUser().id,
          dueAt: result.dueAt,
          priority: result.priority,
          status: 'open',
        })
        .subscribe({
          next: () => {
            toast.add({ severity: 'success', summary: 'Zadanie zlecone' });
            load();
          },
          error: () => toast.add({ severity: 'error', summary: 'Nie udało się zlecić zadania' }),
        });
    },
  };
}
