import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Badge } from 'primeng/badge';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import { Tag } from 'primeng/tag';
import { DatePipe } from '@angular/common';
import type { MessageThread } from '../../models';

/** Left pane of the "Wiadomości" tab: searchable thread list with unread badges. */
@Component({
  selector: 'app-message-thread-list',
  imports: [FormsModule, Badge, IconField, InputIcon, InputText, Tag, DatePipe],
  templateUrl: './message-thread-list.html',
  styleUrl: './message-thread-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'message-thread-list' },
})
export class MessageThreadList {
  readonly threads = input.required<MessageThread[]>();
  readonly selectedThreadId = input<string | null>(null);
  readonly patientLabel = input<(patientId: string) => string>(() => '');

  readonly threadSelected = output<MessageThread>();

  protected readonly searchTerm = signal('');

  protected readonly filteredThreads = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const list = [...this.threads()].sort((a, b) => b.lastMessageAt.localeCompare(a.lastMessageAt));
    if (!term) return list;
    return list.filter((t) => t.subject.toLowerCase().includes(term));
  });

  protected select(thread: MessageThread): void {
    this.threadSelected.emit(thread);
  }
}
