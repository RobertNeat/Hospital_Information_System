import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
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
import { ALERTS } from '../mock-data/alerts.mock';
import { HANDOFF_NOTES } from '../mock-data/handoff-notes.mock';
import { MESSAGE_THREADS } from '../mock-data/message-threads.mock';
import { MESSAGES } from '../mock-data/messages.mock';
import { TASKS } from '../mock-data/tasks.mock';
import type { MessageThread } from '../models';
import type { CursorPage, Page } from '../models/api';
import { TeamMessageService } from './team-message.service';

const page = (items: MessageThread[], n: number, totalPages: number): Page<MessageThread> => ({
  items,
  page: n,
  size: 100,
  totalElements: items.length,
  totalPages,
});

/** A single, last cursor page (`nextBefore: null`) for test flushes. */
const cursorPage = <T>(items: T[]): CursorPage<T> => ({ items, nextBefore: null });

describe('TeamMessageService', () => {
  let service: TeamMessageService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TeamMessageService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getThreads reads every page and feeds the unread counter', async () => {
    const [a, b] = MESSAGE_THREADS;
    const result = firstValueFrom(service.getThreads());
    http
      .expectOne((r) => r.url === MESSAGE_THREADS_URL && r.params.get('page') === '0')
      .flush(page([{ ...a, unreadCount: 2 }], 0, 2));
    http
      .expectOne((r) => r.url === MESSAGE_THREADS_URL && r.params.get('page') === '1')
      .flush(page([{ ...b, unreadCount: 3 }], 1, 2));
    expect(await result).toHaveLength(2);
    expect(service.unreadCount()).toBe(5);
  });

  it('getThreads filtered by patient does not touch the unread counter', async () => {
    const result = firstValueFrom(service.getThreads({ patientId: 'pat-001' }));
    const req = http.expectOne((r) => r.url === MESSAGE_THREADS_URL);
    expect(req.request.params.get('patientId')).toBe('pat-001');
    req.flush(page([{ ...MESSAGE_THREADS[0], unreadCount: 4 }], 0, 1));
    await result;
    expect(service.unreadCount()).toBe(0);
  });

  it('getThread GETs a single thread', async () => {
    const result = firstValueFrom(service.getThread('thr-001'));
    http.expectOne({ method: 'GET', url: messageThreadUrl('thr-001') }).flush(MESSAGE_THREADS[0]);
    expect(await result).toEqual(MESSAGE_THREADS[0]);
  });

  it('getMessages and sendMessage use the thread messages endpoint', async () => {
    const list = firstValueFrom(service.getMessages('thr-001'));
    http
      .expectOne((r) => r.method === 'GET' && r.url === threadMessagesUrl('thr-001'))
      .flush(cursorPage(MESSAGES));
    expect(await list).toEqual([...MESSAGES].reverse());

    const sent = firstValueFrom(service.sendMessage('thr-001', 'Treść', 'high'));
    const req = http.expectOne({ method: 'POST', url: threadMessagesUrl('thr-001') });
    expect(req.request.body).toEqual({ body: 'Treść', priority: 'high' });
    req.flush(MESSAGES[0], { status: 201, statusText: 'Created' });
    await sent;
  });

  it('getMessages follows nextBefore across cursor pages', async () => {
    const [newest, middle, oldest] = MESSAGES;
    const list = firstValueFrom(service.getMessages('thr-001'));
    const firstReq = http.expectOne(
      (r) => r.url === threadMessagesUrl('thr-001') && !r.params.has('before'),
    );
    firstReq.flush({ items: [newest], nextBefore: newest.sentAt });
    const secondReq = http.expectOne(
      (r) => r.url === threadMessagesUrl('thr-001') && r.params.get('before') === newest.sentAt,
    );
    secondReq.flush({ items: [middle, oldest], nextBefore: null });
    expect(await list).toEqual([oldest, middle, newest]);
  });

  it('getAlertsPage exposes a single page with its cursor for load-more UI', async () => {
    const result = firstValueFrom(service.getAlertsPage({ acknowledged: false, size: 1 }));
    const req = http.expectOne((r) => r.url === ALERTS_URL);
    expect(req.request.params.get('size')).toBe('1');
    req.flush({ items: [ALERTS[0]], nextBefore: ALERTS[0].createdAt });
    expect(await result).toEqual({ items: [ALERTS[0]], nextBefore: ALERTS[0].createdAt });
  });

  it('createThread nests the first message', async () => {
    const result = firstValueFrom(
      service.createThread(['stf-002'], 'Temat', 'pat-001', 'Pierwsza wiadomość'),
    );
    const req = http.expectOne({ method: 'POST', url: MESSAGE_THREADS_URL });
    expect(req.request.body).toEqual({
      participantIds: ['stf-002'],
      subject: 'Temat',
      patientId: 'pat-001',
      firstMessage: { body: 'Pierwsza wiadomość', priority: 'normal' },
    });
    req.flush(MESSAGE_THREADS[0], { status: 201, statusText: 'Created' });
    await result;
  });

  it('markThreadRead POSTs an empty body and zeroes the thread unread count', async () => {
    const load = firstValueFrom(service.getThreads());
    http
      .expectOne((r) => r.url === MESSAGE_THREADS_URL)
      .flush(page([{ ...MESSAGE_THREADS[0], id: 'thr-x', unreadCount: 3 }], 0, 1));
    await load;
    expect(service.unreadCount()).toBe(3);

    const result = firstValueFrom(service.markThreadRead('thr-x'));
    const req = http.expectOne({ method: 'POST', url: threadReadUrl('thr-x') });
    expect(req.request.body).toEqual({});
    req.flush({ ...MESSAGE_THREADS[0], id: 'thr-x', unreadCount: 0 });
    await result;
    expect(service.unreadCount()).toBe(0);
  });

  it('getTasks sends only the given filters', async () => {
    const result = firstValueFrom(service.getTasks({ assignedToId: 'stf-001', status: 'open' }));
    const req = http.expectOne((r) => r.url === TASKS_URL);
    expect(req.request.params.get('assignedToId')).toBe('stf-001');
    expect(req.request.params.get('status')).toBe('open');
    expect(req.request.params.has('createdById')).toBe(false);
    req.flush(TASKS);
    expect(await result).toEqual(TASKS);
  });

  it('createTask POSTs the draft without actor or status', async () => {
    const draft = { title: 'Zadanie', assignedToId: 'stf-002', priority: 'normal' as const };
    const result = firstValueFrom(service.createTask(draft));
    const req = http.expectOne({ method: 'POST', url: TASKS_URL });
    expect(req.request.body).toEqual(draft);
    req.flush(TASKS[0], { status: 201, statusText: 'Created' });
    await result;
  });

  it('updateTaskStatus POSTs the status with the version', async () => {
    const result = firstValueFrom(service.updateTaskStatus('tsk-001', 'done', 3));
    const req = http.expectOne({ method: 'POST', url: taskStatusUrl('tsk-001') });
    expect(req.request.body).toEqual({ status: 'done', version: 3 });
    req.flush({ ...TASKS[0], status: 'done' });
    expect((await result).status).toBe('done');
  });

  it('getHandoffNotes filters by ward and shift date; createHandoffNote POSTs the note', async () => {
    const list = firstValueFrom(
      service.getHandoffNotes({ wardId: 'ward-int', shiftDate: '2026-01-01' }),
    );
    const req = http.expectOne((r) => r.url === HANDOFF_NOTES_URL);
    expect(req.request.params.get('wardId')).toBe('ward-int');
    expect(req.request.params.get('shiftDate')).toBe('2026-01-01');
    req.flush(HANDOFF_NOTES);
    await list;

    const note = HANDOFF_NOTES[0];
    const draft = {
      wardId: note.wardId,
      shiftDate: note.shiftDate,
      shift: note.shift,
      toId: note.toId,
      patientNotes: note.patientNotes,
    };
    const created = firstValueFrom(service.createHandoffNote(draft));
    const post = http.expectOne({ method: 'POST', url: HANDOFF_NOTES_URL });
    expect(post.request.body).toEqual(draft);
    post.flush(HANDOFF_NOTES[0], { status: 201, statusText: 'Created' });
    await created;
  });

  it('getAlerts passes the acknowledged filter and the unacknowledged list feeds the badge', async () => {
    const unacknowledged = ALERTS.filter((a) => !a.acknowledged);
    const result = firstValueFrom(service.getAlerts({ acknowledged: false }));
    const req = http.expectOne((r) => r.url === ALERTS_URL);
    expect(req.request.params.get('acknowledged')).toBe('false');
    req.flush(cursorPage(unacknowledged));
    await result;
    expect(service.unacknowledgedAlertCount()).toBe(unacknowledged.length);
    expect(service.alerts()).toEqual(unacknowledged);
  });

  it('getAlerts for one patient does not replace the badge list', async () => {
    const result = firstValueFrom(service.getAlerts({ patientId: 'pat-002', acknowledged: false }));
    http.expectOne((r) => r.url === ALERTS_URL).flush(cursorPage([ALERTS[0]]));
    await result;
    expect(service.alerts()).toEqual([]);
  });

  it('acknowledgeAlert POSTs an empty body and drops the alert from the badge list', async () => {
    const unacknowledged = ALERTS.filter((a) => !a.acknowledged);
    const load = firstValueFrom(service.getAlerts({ acknowledged: false }));
    http.expectOne((r) => r.url === ALERTS_URL).flush(cursorPage(unacknowledged));
    await load;

    const target = unacknowledged[0];
    const result = firstValueFrom(service.acknowledgeAlert(target.id));
    const req = http.expectOne({ method: 'POST', url: alertAcknowledgeUrl(target.id) });
    expect(req.request.body).toEqual({});
    req.flush({ ...target, acknowledged: true });
    expect((await result).acknowledged).toBe(true);
    expect(service.alerts().some((a) => a.id === target.id)).toBe(false);
  });

  it('refresh reloads threads and unacknowledged alerts', () => {
    service.refresh();
    http.expectOne((r) => r.url === MESSAGE_THREADS_URL).flush(page([], 0, 1));
    const alerts = http.expectOne((r) => r.url === ALERTS_URL);
    expect(alerts.request.params.get('acknowledged')).toBe('false');
    alerts.flush(cursorPage([]));
  });

  it('applyPush upserts threads, dedupes alerts and drops acknowledged ones', () => {
    const thread = { ...MESSAGE_THREADS[0], unreadCount: 4 };
    const seen: string[] = [];
    service.pushed$.subscribe((p) => seen.push(p.kind));
    service.applyPush({ kind: 'thread', thread });
    expect(service.unreadCount()).toBe(4);
    service.applyPush({ kind: 'thread', thread: { ...thread, unreadCount: 0 } });
    expect(service.unreadCount()).toBe(0);

    const alert = { ...ALERTS[0], acknowledged: false };
    service.applyPush({ kind: 'alert', alert });
    service.applyPush({ kind: 'alert', alert });
    expect(service.unacknowledgedAlertCount()).toBe(1);
    service.applyPush({ kind: 'alert', alert: { ...alert, acknowledged: true } });
    expect(service.unacknowledgedAlertCount()).toBe(0);
    expect(seen).toEqual(['thread', 'thread', 'alert', 'alert', 'alert']);
  });
});
