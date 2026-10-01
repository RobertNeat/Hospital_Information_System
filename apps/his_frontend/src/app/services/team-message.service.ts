import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import type { Signal } from '@angular/core';
import { Subject, forkJoin, tap } from 'rxjs';
import type { Observable } from 'rxjs';
import {
  ALERTS_URL,
  HANDOFF_NOTES_URL,
  MESSAGE_THREADS_URL,
  TASKS_URL,
  alertAcknowledgeUrl,
  messageThreadUrl,
  taskStatusUrl,
  threadMessagesUrl,
  threadReadUrl,
} from '../config/api.config';
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
import type {
  AlertAcknowledgeRequest,
  AlertQuery,
  HandoffNoteCreateRequest,
  HandoffNoteQuery,
  MessageSendRequest,
  Page,
  TaskCreateRequest,
  TaskQuery,
  TaskStatusUpdateRequest,
  ThreadCreateRequest,
  ThreadMarkReadRequest,
  ThreadQuery,
} from '../models/api';
import { toHttpParams } from '../utils/http-params';
import { MAX_PAGE_SIZE, readAllPages } from '../utils/read-all-pages';

/** Server push applied to the local state; pages subscribe to reload what they display. */
export type TeamPush =
  | { kind: 'message'; message: Message }
  | { kind: 'thread'; thread: MessageThread }
  | { kind: 'alert'; alert: ClinicalAlert }
  | { kind: 'task'; task: TeamTask }
  /** The push connection was re-established; events in between may have been missed. */
  | { kind: 'resync' };

/**
 * Threads, messages, tasks, handoff notes and alerts backed by `/message-threads`, `/tasks`,
 * `/handoff-notes` and `/alerts`. Named `TeamMessageService` to avoid clashing with PrimeNG's
 * `MessageService`. The backend takes the actor from the token; threads are visible only to their
 * participants (403 otherwise); alerts are created by the backend only and acknowledged per user.
 * Failures arrive as `HttpErrorResponse` with a `ProblemDetail` body (see `toApiError`).
 */
@Injectable({ providedIn: 'root' })
export class TeamMessageService {
  private readonly http = inject(HttpClient);

  private readonly threadsState = signal<MessageThread[]>([]);
  private readonly alertsState = signal<ClinicalAlert[]>([]);

  /** Sum of `unreadCount` over the signed-in user's threads (as of the last refresh). */
  readonly unreadCount: Signal<number> = computed(() =>
    this.threadsState().reduce((sum, t) => sum + t.unreadCount, 0),
  );

  /** Alerts the signed-in user has not acknowledged yet, newest first (as of the last refresh). */
  readonly alerts: Signal<ClinicalAlert[]> = this.alertsState.asReadonly();

  readonly unacknowledgedAlertCount: Signal<number> = computed(() => this.alertsState().length);

  private readonly pushes = new Subject<TeamPush>();

  /** Push events (STOMP, see `RealtimeService`) after they were applied to the signals above. */
  readonly pushed$: Observable<TeamPush> = this.pushes.asObservable();

  /**
   * Reloads the thread list and the unacknowledged alerts behind `unreadCount`, `alerts` and
   * `unacknowledgedAlertCount`.
   */
  refresh(): void {
    forkJoin([this.getThreads(), this.getAlerts({ acknowledged: false })]).subscribe({
      error: () => undefined,
    });
  }

  /**
   * Threads of the signed-in user, newest activity first (every page is read). An unfiltered
   * call also refreshes `unreadCount`.
   */
  getThreads(query?: ThreadQuery): Observable<MessageThread[]> {
    return readAllPages((page) =>
      this.http.get<Page<MessageThread>>(MESSAGE_THREADS_URL, {
        params: toHttpParams({
          patientId: query?.patientId,
          sort: query?.sort,
          page,
          size: MAX_PAGE_SIZE,
        }),
      }),
    ).pipe(
      tap((list) => {
        if (!query?.patientId) this.threadsState.set(list);
      }),
    );
  }

  /** 403 for a non-participant, 404 for an unknown thread. */
  getThread(threadId: ID): Observable<MessageThread> {
    return this.http.get<MessageThread>(messageThreadUrl(threadId));
  }

  /** Messages of a thread, oldest first. */
  getMessages(threadId: ID): Observable<Message[]> {
    return this.http.get<Message[]>(threadMessagesUrl(threadId));
  }

  sendMessage(threadId: ID, body: string, priority: Priority): Observable<Message> {
    const request: MessageSendRequest = { body, priority };
    return this.http.post<Message>(threadMessagesUrl(threadId), request);
  }

  /** The creator joins as a participant automatically; the first message is sent as `normal`. */
  createThread(
    participantIds: ID[],
    subject: string,
    patientId: ID | undefined,
    firstMessage: string,
  ): Observable<MessageThread> {
    const request: ThreadCreateRequest = {
      participantIds,
      subject,
      patientId,
      firstMessage: { body: firstMessage, priority: 'normal' },
    };
    return this.http.post<MessageThread>(MESSAGE_THREADS_URL, request);
  }

  /** Moves the signed-in user's read cursor; the response has `unreadCount = 0`. */
  markThreadRead(threadId: ID): Observable<MessageThread> {
    const request: ThreadMarkReadRequest = {};
    return this.http
      .post<MessageThread>(threadReadUrl(threadId), request)
      .pipe(
        tap((updated) =>
          this.threadsState.update((list) => list.map((t) => (t.id === updated.id ? updated : t))),
        ),
      );
  }

  /** Tasks, newest first (not paged). */
  getTasks(filter?: TaskQuery): Observable<TeamTask[]> {
    return this.http.get<TeamTask[]>(TASKS_URL, {
      params: toHttpParams({
        assignedToId: filter?.assignedToId,
        createdById: filter?.createdById,
        patientId: filter?.patientId,
        status: filter?.status,
      }),
    });
  }

  /** The new task is `open` and its creator comes from the token. */
  createTask(draft: TaskCreateRequest): Observable<TeamTask> {
    return this.http.post<TeamTask>(TASKS_URL, draft);
  }

  /** Assignee or creator only (403); pass the loaded `version`; 409 on a forbidden transition or stale version. */
  updateTaskStatus(id: ID, status: TaskStatus, version?: number): Observable<TeamTask> {
    const request: TaskStatusUpdateRequest = { status, version };
    return this.http.post<TeamTask>(taskStatusUrl(id), request);
  }

  getHandoffNotes(query?: HandoffNoteQuery): Observable<HandoffNote[]> {
    return this.http.get<HandoffNote[]>(HANDOFF_NOTES_URL, {
      params: toHttpParams({ wardId: query?.wardId, shiftDate: query?.shiftDate }),
    });
  }

  /** `fromId` comes from the token; 422 lists unknown `wardId`/`toId`/patients in `errors[]`. */
  createHandoffNote(draft: HandoffNoteCreateRequest): Observable<HandoffNote> {
    return this.http.post<HandoffNote>(HANDOFF_NOTES_URL, draft);
  }

  /** Alerts, newest first; `acknowledged` refers to the signed-in user. Not paged. */
  getAlerts(filter?: AlertQuery): Observable<ClinicalAlert[]> {
    return this.http
      .get<ClinicalAlert[]>(ALERTS_URL, {
        params: toHttpParams({
          patientId: filter?.patientId,
          acknowledged: filter?.acknowledged,
        }),
      })
      .pipe(
        tap((list) => {
          if (filter?.acknowledged === false && !filter.patientId) this.alertsState.set(list);
        }),
      );
  }

  /** Per-user and idempotent; the acknowledging user comes from the token. */
  acknowledgeAlert(id: ID): Observable<ClinicalAlert> {
    const request: AlertAcknowledgeRequest = {};
    return this.http
      .post<ClinicalAlert>(alertAcknowledgeUrl(id), request)
      .pipe(tap(() => this.alertsState.update((list) => list.filter((a) => a.id !== id))));
  }

  /** Applies a pushed `/user/queue/*` or `/topic/alerts/*` payload (`unreadCount` is the viewer's). */
  applyPush(push: TeamPush): void {
    switch (push.kind) {
      case 'thread': {
        const { thread } = push;
        this.threadsState.update((list) =>
          list.some((t) => t.id === thread.id)
            ? list.map((t) => (t.id === thread.id ? thread : t))
            : [thread, ...list],
        );
        break;
      }
      case 'alert': {
        // Ward broadcasts and user queue can deliver the same alert: dedupe by id.
        const { alert } = push;
        this.alertsState.update((list) => {
          const rest = list.filter((a) => a.id !== alert.id);
          return alert.acknowledged ? rest : [alert, ...rest];
        });
        break;
      }
      default:
        break;
    }
    this.pushes.next(push);
  }
}
