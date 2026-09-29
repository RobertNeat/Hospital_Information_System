import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { MessageThreadList } from './message-thread-list';
import type { MessageThread } from '../../models';

const THREADS: MessageThread[] = [
  {
    id: 'thr-001',
    participantIds: ['stf-001', 'stf-002'],
    subject: 'Konsultacja pacjenta',
    patientId: 'pat-001',
    lastMessageAt: '2026-09-28T10:00:00.000Z',
    unreadCount: 2,
  },
  {
    id: 'thr-002',
    participantIds: ['stf-001', 'stf-003'],
    subject: 'Dyżur nocny',
    lastMessageAt: '2026-09-29T08:00:00.000Z',
    unreadCount: 0,
  },
];

describe('MessageThreadList', () => {
  it('renders all threads sorted by most recent message', async () => {
    const fixture = TestBed.createComponent(MessageThreadList);
    fixture.componentRef.setInput('threads', THREADS);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Konsultacja pacjenta');
    expect(el.textContent).toContain('Dyżur nocny');
  });

  it('emits threadSelected when an item is clicked', async () => {
    const fixture = TestBed.createComponent(MessageThreadList);
    fixture.componentRef.setInput('threads', THREADS);
    await fixture.whenStable();

    let selected: MessageThread | undefined;
    fixture.componentInstance.threadSelected.subscribe((t) => (selected = t));

    const button = (fixture.nativeElement as HTMLElement).querySelector(
      '.his-thread-list__item',
    ) as HTMLButtonElement;
    button.click();
    await fixture.whenStable();

    expect(selected).toBeDefined();
  });
});
