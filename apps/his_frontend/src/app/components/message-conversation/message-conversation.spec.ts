import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { MessageConversation } from './message-conversation';
import type { Message } from '../../models';

const MESSAGES: Message[] = [
  {
    id: 'msg-001',
    threadId: 'thr-001',
    senderId: 'stf-001',
    sentAt: '2026-09-29T08:00:00.000Z',
    body: 'Proszę o kontrolę parametrów.',
    priority: 'normal',
    readByIds: ['stf-001'],
  },
  {
    id: 'msg-002',
    threadId: 'thr-001',
    senderId: 'stf-002',
    sentAt: '2026-09-29T08:05:00.000Z',
    body: 'Zrobione, wszystko w normie.',
    priority: 'high',
    readByIds: ['stf-002'],
  },
];

describe('MessageConversation', () => {
  it('renders messages in chronological order', async () => {
    const fixture = TestBed.createComponent(MessageConversation);
    fixture.componentRef.setInput('messages', MESSAGES);
    fixture.componentRef.setInput('currentUserId', 'stf-001');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Proszę o kontrolę parametrów.');
    expect(el.textContent).toContain('Zrobione, wszystko w normie.');
  });

  it('emits send with the composed body and priority, and clears the draft', async () => {
    const fixture = TestBed.createComponent(MessageConversation);
    fixture.componentRef.setInput('messages', MESSAGES);
    fixture.componentRef.setInput('currentUserId', 'stf-001');
    await fixture.whenStable();

    let emitted: { body: string; priority: string } | undefined;
    fixture.componentInstance.send.subscribe((e) => (emitted = e));

    (
      fixture.componentInstance as unknown as { draftBody: { set: (v: string) => void } }
    ).draftBody.set('Nowa wiadomość');
    (fixture.componentInstance as unknown as { submit: () => void }).submit();

    expect(emitted).toEqual({ body: 'Nowa wiadomość', priority: 'normal' });
  });

  it('does not emit send for an empty draft', () => {
    const fixture = TestBed.createComponent(MessageConversation);
    fixture.componentRef.setInput('messages', MESSAGES);
    fixture.componentRef.setInput('currentUserId', 'stf-001');

    let emitted = false;
    fixture.componentInstance.send.subscribe(() => (emitted = true));
    (fixture.componentInstance as unknown as { submit: () => void }).submit();

    expect(emitted).toBe(false);
  });
});
