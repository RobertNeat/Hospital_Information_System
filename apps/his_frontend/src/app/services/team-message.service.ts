import { Injectable, computed, inject, signal } from '@angular/core';
import type { Signal } from '@angular/core';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { ALERTS } from '../mock-data/alerts.mock';
import { HANDOFF_NOTES } from '../mock-data/handoff-notes.mock';
import { MESSAGE_THREADS } from '../mock-data/message-threads.mock';
import { MESSAGES } from '../mock-data/messages.mock';
import { TASKS } from '../mock-data/tasks.mock';
import type {
  ClinicalAlert,
  HandoffNote,
  ID,
  Message,
  MessageThread,
  Priority,
  TaskStatus,
  TeamTask,
} from '../models';
import { mockError, mockResponse, nextId } from '../utils/mock-response';

/** Named `TeamMessageService` to avoid clashing with PrimeNG's `MessageService`. */
@Injectable({ providedIn: 'root' })
export class TeamMessageService {
  private readonly latency = inject(MOCK_LATENCY_MS);

  private readonly threads = signal<MessageThread[]>(structuredClone(MESSAGE_THREADS));
  private readonly messages: Message[] = structuredClone(MESSAGES);
  private readonly tasks = signal<TeamTask[]>(structuredClone(TASKS));
  private readonly handoffNotes: HandoffNote[] = structuredClone(HANDOFF_NOTES);
  private readonly alerts = signal<ClinicalAlert[]>(structuredClone(ALERTS));

  private messageSequence = this.messages.length;
  private threadSequence = this.threads().length;
  private taskSequence = this.tasks().length;
  private handoffSequence = this.handoffNotes.length;
  private alertSequence = this.alerts().length;

  /** `// TODO: WebSocket transport (STOMP/Socket) -- replace in-memory Subject */
  readonly realtimeMode = 'mock' as const;

  readonly unreadCount: Signal<number> = computed(() =>
    this.threads().reduce((sum, t) => sum + t.unreadCount, 0),
  );

  readonly unacknowledgedAlertCount: Signal<number> = computed(
    () => this.alerts().filter((a) => !a.acknowledged).length,
  );

  getThreads(userId: ID): Observable<MessageThread[]> {
    return mockResponse(
      this.threads().filter((t) => t.participantIds.includes(userId)),
      this.latency,
    );
  }

  getMessages(threadId: ID): Observable<Message[]> {
    return mockResponse(
      this.messages.filter((m) => m.threadId === threadId),
      this.latency,
    );
  }

  sendMessage(threadId: ID, body: string, priority: Priority): Observable<Message> {
    this.messageSequence++;
    const message: Message = {
      id: nextId('msg', this.messageSequence),
      threadId,
      senderId: 'stf-001',
      sentAt: new Date().toISOString(),
      body,
      priority,
      readByIds: ['stf-001'],
    };
    this.messages.push(message);
    this.threads.update((list) =>
      list.map((t) => (t.id === threadId ? { ...t, lastMessageAt: message.sentAt } : t)),
    );
    return mockResponse(message, this.latency);
  }

  createThread(
    participantIds: ID[],
    subject: string,
    patientId: ID | undefined,
    firstMessage: string,
  ): Observable<MessageThread> {
    this.threadSequence++;
    this.messageSequence++;
    const now = new Date().toISOString();
    const thread: MessageThread = {
      id: nextId('thr', this.threadSequence),
      participantIds,
      subject,
      patientId,
      lastMessageAt: now,
      unreadCount: 0,
    };
    this.threads.update((list) => [...list, thread]);
    this.messages.push({
      id: nextId('msg', this.messageSequence),
      threadId: thread.id,
      senderId: 'stf-001',
      sentAt: now,
      body: firstMessage,
      priority: 'normal',
      readByIds: ['stf-001'],
    });
    return mockResponse(thread, this.latency);
  }

  markThreadRead(threadId: ID, userId: ID): Observable<MessageThread> {
    let updatedThread: MessageThread | undefined;
    this.threads.update((list) =>
      list.map((t) => {
        if (t.id !== threadId) return t;
        updatedThread = { ...t, unreadCount: 0 };
        return updatedThread;
      }),
    );
    for (const m of this.messages) {
      if (m.threadId === threadId && !m.readByIds.includes(userId)) {
        m.readByIds.push(userId);
      }
    }
    if (!updatedThread) return mockError(`Nie znaleziono wątku o id ${threadId}`, this.latency);
    return mockResponse(updatedThread, this.latency);
  }

  getTasks(filter?: {
    assignedToId?: ID;
    createdById?: ID;
    patientId?: ID;
    status?: TaskStatus;
  }): Observable<TeamTask[]> {
    let result = this.tasks();
    if (filter?.assignedToId) result = result.filter((t) => t.assignedToId === filter.assignedToId);
    if (filter?.createdById) result = result.filter((t) => t.createdById === filter.createdById);
    if (filter?.patientId) result = result.filter((t) => t.patientId === filter.patientId);
    if (filter?.status) result = result.filter((t) => t.status === filter.status);
    return mockResponse(result, this.latency);
  }

  createTask(draft: Omit<TeamTask, 'id' | 'createdAt'>): Observable<TeamTask> {
    this.taskSequence++;
    const task: TeamTask = {
      ...draft,
      id: nextId('tsk', this.taskSequence),
      createdAt: new Date().toISOString(),
    };
    this.tasks.update((list) => [...list, task]);
    return mockResponse(task, this.latency);
  }

  updateTaskStatus(id: ID, status: TaskStatus): Observable<TeamTask> {
    let updated: TeamTask | undefined;
    this.tasks.update((list) =>
      list.map((t) => {
        if (t.id !== id) return t;
        updated = { ...t, status };
        return updated;
      }),
    );
    if (!updated) return mockError(`Nie znaleziono zadania o id ${id}`, this.latency);
    return mockResponse(updated, this.latency);
  }

  getHandoffNotes(wardId?: ID): Observable<HandoffNote[]> {
    const result = wardId
      ? this.handoffNotes.filter((h) => h.wardId === wardId)
      : this.handoffNotes;
    return mockResponse(result, this.latency);
  }

  createHandoffNote(draft: Omit<HandoffNote, 'id' | 'createdAt'>): Observable<HandoffNote> {
    this.handoffSequence++;
    const note: HandoffNote = {
      ...draft,
      id: nextId('hon', this.handoffSequence),
      createdAt: new Date().toISOString(),
    };
    this.handoffNotes.push(note);
    return mockResponse(note, this.latency);
  }

  getAlerts(filter?: { patientId?: ID; acknowledged?: boolean }): Observable<ClinicalAlert[]> {
    let result = this.alerts();
    if (filter?.patientId) result = result.filter((a) => a.patientId === filter.patientId);
    if (filter?.acknowledged !== undefined) {
      result = result.filter((a) => a.acknowledged === filter.acknowledged);
    }
    return mockResponse(result, this.latency);
  }

  acknowledgeAlert(id: ID, userId: ID): Observable<ClinicalAlert> {
    let updated: ClinicalAlert | undefined;
    this.alerts.update((list) =>
      list.map((a) => {
        if (a.id !== id) return a;
        updated = { ...a, acknowledged: true, acknowledgedById: userId };
        return updated;
      }),
    );
    if (!updated) return mockError(`Nie znaleziono alertu o id ${id}`, this.latency);
    return mockResponse(updated, this.latency);
  }

  pushAlert(
    alert: Omit<ClinicalAlert, 'id' | 'createdAt' | 'acknowledged'>,
  ): Observable<ClinicalAlert> {
    this.alertSequence++;
    const newAlert: ClinicalAlert = {
      ...alert,
      id: nextId('alr', this.alertSequence),
      createdAt: new Date().toISOString(),
      acknowledged: false,
    };
    this.alerts.update((list) => [newAlert, ...list]);
    return mockResponse(newAlert, this.latency);
  }
}
