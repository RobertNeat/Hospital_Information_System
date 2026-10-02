import { computed, signal } from '@angular/core';
import { NEVER, of, throwError } from 'rxjs';
import { ALERTS } from '../mock-data/alerts.mock';
import { HANDOFF_NOTES } from '../mock-data/handoff-notes.mock';
import { MESSAGE_THREADS } from '../mock-data/message-threads.mock';
import { MESSAGES } from '../mock-data/messages.mock';
import { TASKS } from '../mock-data/tasks.mock';
import type {
  ClinicalAlert,
  HandoffNote,
  Message,
  MessageThread,
  Priority,
  TaskStatus,
  TeamTask,
} from '../models';
import type {
  AlertQuery,
  HandoffNoteCreateRequest,
  HandoffNoteQuery,
  TaskCreateRequest,
  TaskQuery,
  ThreadQuery,
} from '../models/api';
import { TeamMessageService } from '../services/team-message.service';
import { TEST_USER } from './staff-service.stub';

/** In-memory `TeamMessageService` double over the mock messaging data (fresh state per TestBed). */
export function createTeamMessageServiceStub(): Partial<Record<keyof TeamMessageService, unknown>> {
  const me = TEST_USER.id;
  const threads = signal<MessageThread[]>(
    structuredClone(MESSAGE_THREADS).filter((t) => t.participantIds.includes(me)),
  );
  const messages: Message[] = structuredClone(MESSAGES);
  const tasks: TeamTask[] = structuredClone(TASKS);
  const notes: HandoffNote[] = structuredClone(HANDOFF_NOTES);
  const allAlerts = signal<ClinicalAlert[]>(structuredClone(ALERTS));
  const unacknowledged = computed(() => allAlerts().filter((a) => !a.acknowledged));
  const notFound = (what: string) => throwError(() => new Error(`Nie znaleziono: ${what}`));

  return {
    unreadCount: computed(() => threads().reduce((sum, t) => sum + t.unreadCount, 0)),
    alerts: unacknowledged,
    unacknowledgedAlertCount: computed(() => unacknowledged().length),
    refresh: () => undefined,
    pushed$: NEVER,
    applyPush: () => undefined,
    getThreads: (query?: ThreadQuery) =>
      of(threads().filter((t) => !query?.patientId || t.patientId === query.patientId)),
    getThread: (id: string) => {
      const found = threads().find((t) => t.id === id);
      return found ? of(found) : notFound(id);
    },
    getMessages: (threadId: string) => of(messages.filter((m) => m.threadId === threadId)),
    getMessagesPage: (threadId: string) =>
      of({ items: messages.filter((m) => m.threadId === threadId), nextBefore: null }),
    sendMessage: (threadId: string, body: string, priority: Priority) => {
      const message: Message = {
        id: `test-msg-${messages.length + 1}`,
        threadId,
        senderId: me,
        sentAt: new Date().toISOString(),
        body,
        priority,
        readByIds: [me],
      };
      messages.push(message);
      return of(message);
    },
    createThread: (
      participantIds: string[],
      subject: string,
      patientId: string | undefined,
      firstMessage: string,
    ) => {
      const now = new Date().toISOString();
      const thread: MessageThread = {
        id: `test-thr-${threads().length + 1}`,
        participantIds: [...new Set([me, ...participantIds])],
        subject,
        patientId,
        createdById: me,
        lastMessageAt: now,
        unreadCount: 0,
      };
      threads.update((list) => [...list, thread]);
      messages.push({
        id: `test-msg-${messages.length + 1}`,
        threadId: thread.id,
        senderId: me,
        sentAt: now,
        body: firstMessage,
        priority: 'normal',
        readByIds: [me],
      });
      return of(thread);
    },
    markThreadRead: (threadId: string) => {
      const found = threads().find((t) => t.id === threadId);
      if (!found) return notFound(threadId);
      const updated = { ...found, unreadCount: 0 };
      threads.update((list) => list.map((t) => (t.id === threadId ? updated : t)));
      return of(updated);
    },
    getTasks: (filter?: TaskQuery) =>
      of(
        tasks.filter(
          (t) =>
            (!filter?.assignedToId || t.assignedToId === filter.assignedToId) &&
            (!filter?.createdById || t.createdById === filter.createdById) &&
            (!filter?.patientId || t.patientId === filter.patientId) &&
            (!filter?.status || t.status === filter.status),
        ),
      ),
    createTask: (draft: TaskCreateRequest) => {
      const task: TeamTask = {
        ...draft,
        id: `test-tsk-${tasks.length + 1}`,
        createdById: me,
        createdAt: new Date().toISOString(),
        status: 'open',
      };
      tasks.push(task);
      return of(task);
    },
    updateTaskStatus: (id: string, status: TaskStatus) => {
      const index = tasks.findIndex((t) => t.id === id);
      if (index === -1) return notFound(id);
      tasks[index] = { ...tasks[index], status };
      return of(tasks[index]);
    },
    getHandoffNotes: (query?: HandoffNoteQuery) =>
      of(notes.filter((n) => !query?.wardId || n.wardId === query.wardId)),
    createHandoffNote: (draft: HandoffNoteCreateRequest) => {
      const note: HandoffNote = {
        ...draft,
        id: `test-hon-${notes.length + 1}`,
        fromId: me,
        createdAt: new Date().toISOString(),
      };
      notes.push(note);
      return of(note);
    },
    getAlerts: (filter?: AlertQuery) =>
      of(
        allAlerts().filter(
          (a) =>
            (!filter?.patientId || a.patientId === filter.patientId) &&
            (filter?.acknowledged === undefined || a.acknowledged === filter.acknowledged),
        ),
      ),
    getAlertsPage: (filter?: AlertQuery) =>
      of({
        items: allAlerts().filter(
          (a) =>
            (!filter?.patientId || a.patientId === filter.patientId) &&
            (filter?.acknowledged === undefined || a.acknowledged === filter.acknowledged),
        ),
        nextBefore: null,
      }),
    acknowledgeAlert: (id: string) => {
      const found = allAlerts().find((a) => a.id === id);
      if (!found) return notFound(id);
      const updated: ClinicalAlert = {
        ...found,
        acknowledged: true,
        acknowledgedById: me,
        acknowledgedAt: new Date().toISOString(),
      };
      allAlerts.update((list) => list.map((a) => (a.id === id ? updated : a)));
      return of(updated);
    },
  };
}

/** Test provider replacing the HTTP-backed `TeamMessageService` with the mock messaging data. */
export const teamMessageServiceStub = {
  provide: TeamMessageService,
  useFactory: createTeamMessageServiceStub,
};
