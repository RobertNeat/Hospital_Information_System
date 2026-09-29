import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { Button } from 'primeng/button';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { PRIORITY_OPTIONS } from '../../constants/labels';
import { StatusTag } from '../status-tag/status-tag';
import type { Message, Priority } from '../../models';

/** Right pane of the "Wiadomości" tab: message bubbles + composer. */
@Component({
  selector: 'app-message-conversation',
  imports: [FormsModule, DatePipe, Button, Select, Textarea, StatusTag],
  templateUrl: './message-conversation.html',
  styleUrl: './message-conversation.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'message-conversation' },
})
export class MessageConversation {
  readonly messages = input.required<Message[]>();
  readonly currentUserId = input.required<string>();
  readonly senderLabel = input<(senderId: string) => string>((id) => id);
  readonly subject = input<string>('');
  readonly sending = input(false);

  readonly send = output<{ body: string; priority: Priority }>();

  protected readonly priorityOptions = PRIORITY_OPTIONS;

  protected readonly draftBody = signal('');
  protected readonly draftPriority = signal<Priority>('normal');

  protected readonly orderedMessages = computed(() =>
    [...this.messages()].sort((a, b) => a.sentAt.localeCompare(b.sentAt)),
  );

  protected isOwn(message: Message): boolean {
    return message.senderId === this.currentUserId();
  }

  protected submit(): void {
    const body = this.draftBody().trim();
    if (!body) return;
    this.send.emit({ body, priority: this.draftPriority() });
    this.draftBody.set('');
    this.draftPriority.set('normal');
  }

  protected onComposerKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      this.submit();
    }
  }
}
