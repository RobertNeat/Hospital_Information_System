import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { TeamMessageService } from './team-message.service';

describe('TeamMessageService', () => {
  let service: TeamMessageService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    service = TestBed.inject(TeamMessageService);
  });

  it('getThreads filters by participant', async () => {
    const threads = await firstValueFrom(service.getThreads('stf-001'));
    expect(threads.every((t) => t.participantIds.includes('stf-001'))).toBe(true);
    expect(threads.length).toBeGreaterThan(0);
  });

  it('sendMessage persists a new message and updates thread.lastMessageAt', async () => {
    const before = await firstValueFrom(service.getMessages('thr-001'));
    const msg = await firstValueFrom(service.sendMessage('thr-001', 'Test message', 'normal'));
    expect(msg.body).toBe('Test message');

    const after = await firstValueFrom(service.getMessages('thr-001'));
    expect(after.length).toBe(before.length + 1);
  });

  it('createThread creates a thread with a first message', async () => {
    const thread = await firstValueFrom(
      service.createThread(
        ['stf-001', 'stf-002'],
        'Nowy temat',
        undefined,
        'Treść pierwszej wiadomości',
      ),
    );
    expect(thread.subject).toBe('Nowy temat');
    const messages = await firstValueFrom(service.getMessages(thread.id));
    expect(messages).toHaveLength(1);
  });

  it('getTasks filters by assignedToId and status', async () => {
    const tasks = await firstValueFrom(
      service.getTasks({ assignedToId: 'stf-006', status: 'done' }),
    );
    expect(tasks.every((t) => t.assignedToId === 'stf-006' && t.status === 'done')).toBe(true);
  });

  it('createTask persists a new task', async () => {
    const before = await firstValueFrom(service.getTasks());
    const task = await firstValueFrom(
      service.createTask({
        title: 'Test task',
        assignedToId: 'stf-006',
        createdById: 'stf-001',
        priority: 'normal',
        status: 'open',
      }),
    );
    expect(task.id).toMatch(/^tsk-\d+$/);
    const after = await firstValueFrom(service.getTasks());
    expect(after.length).toBe(before.length + 1);
  });

  it('updateTaskStatus changes the task status', async () => {
    const updated = await firstValueFrom(service.updateTaskStatus('tsk-002', 'in_progress'));
    expect(updated.status).toBe('in_progress');
  });

  it('getHandoffNotes filters by ward', async () => {
    const notes = await firstValueFrom(service.getHandoffNotes('ward-int'));
    expect(notes.every((n) => n.wardId === 'ward-int')).toBe(true);
  });

  it('getAlerts filters by acknowledged', async () => {
    const unacknowledged = await firstValueFrom(service.getAlerts({ acknowledged: false }));
    expect(unacknowledged.every((a) => !a.acknowledged)).toBe(true);
  });

  it('acknowledgeAlert marks the alert acknowledged', async () => {
    const updated = await firstValueFrom(service.acknowledgeAlert('alr-001', 'stf-001'));
    expect(updated.acknowledged).toBe(true);
    expect(updated.acknowledgedById).toBe('stf-001');
  });

  it('pushAlert adds a new unacknowledged alert', async () => {
    const before = await firstValueFrom(service.getAlerts());
    const alert = await firstValueFrom(
      service.pushAlert({ type: 'system', severity: 'info', message: 'Test alert' }),
    );
    expect(alert.acknowledged).toBe(false);
    const after = await firstValueFrom(service.getAlerts());
    expect(after.length).toBe(before.length + 1);
  });

  it('unreadCount and unacknowledgedAlertCount are derived signals', () => {
    expect(typeof service.unreadCount()).toBe('number');
    expect(typeof service.unacknowledgedAlertCount()).toBe('number');
    expect(service.unreadCount()).toBeGreaterThan(0);
  });
});
