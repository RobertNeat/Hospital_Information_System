import { computed, inject, signal, type Signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import type { Message as TeamMessage, MessageThread, PatientSummary } from '../../models';
import { StaffService } from '../../services/staff.service';
import { TeamMessageService } from '../../services/team-message.service';
import type { MessagesContext } from './messages.context';

/** "Wiadomości" tab: thread list, conversation and the new-message dialog. Injection context only. */
export function createInboxState(
  ctx: MessagesContext,
  preselectedThread: Signal<string | undefined>,
) {
  const service = inject(TeamMessageService);
  const staffService = inject(StaffService);
  const fb = inject(FormBuilder).nonNullable;
  const { toast, router, patients } = ctx;

  const threads = signal<MessageThread[]>([]);
  const threadsLoading = signal(true);
  const selectedThreadId = signal<string | null>(null);
  const selectedThreadMessages = signal<TeamMessage[]>([]);
  const conversationSending = signal(false);
  const selectedThread = computed(() => threads().find((t) => t.id === selectedThreadId()) ?? null);

  const newMessageDialogVisible = signal(false);
  const newMessageForm = fb.group({
    recipientIds: fb.control<string[]>([], [Validators.required]),
    subject: fb.control('', [Validators.required]),
    body: fb.control('', [Validators.required]),
  });
  const newMessagePatient = signal<PatientSummary | null>(null);

  const reloadMessages = (threadId: string): void => {
    service.getMessages(threadId).subscribe((messages) => selectedThreadMessages.set(messages));
  };

  const selectThread = (threadId: string): void => {
    selectedThreadId.set(threadId);
    reloadMessages(threadId);
    service.markThreadRead(threadId, ctx.currentUser().id).subscribe((updated) => {
      threads.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
    });
  };

  const openThreadInUrl = (threadId: string): void => {
    void router.navigate([], {
      queryParams: { tab: 'inbox', thread: threadId },
      queryParamsHandling: 'merge',
    });
  };

  const load = (): void => {
    threadsLoading.set(true);
    service.getThreads(ctx.currentUser().id).subscribe((list) => {
      threads.set(list);
      threadsLoading.set(false);
      for (const t of list) {
        if (t.patientId) patients.resolve(t.patientId);
      }
      const preselect = preselectedThread() ?? list[0]?.id ?? null;
      if (preselect) selectThread(preselect);
    });
  };

  return {
    threads,
    threadsLoading,
    selectedThreadId,
    selectedThreadMessages,
    conversationSending,
    selectedThread,
    newMessageDialogVisible,
    newMessageForm,
    newMessagePatient,
    load,
    staffLabel: (staffId: string): string => staffService.nameOf(staffId),

    onThreadSelected: (t: MessageThread): void => {
      openThreadInUrl(t.id);
      selectThread(t.id);
    },

    sendMessage: (event: { body: string; priority: 'normal' | 'high' | 'critical' }): void => {
      const threadId = selectedThreadId();
      if (!threadId) return;
      conversationSending.set(true);
      service.sendMessage(threadId, event.body, event.priority).subscribe({
        next: () => {
          conversationSending.set(false);
          reloadMessages(threadId);
          toast.add({ severity: 'success', summary: 'Wysłano wiadomość' });
        },
        error: () => {
          conversationSending.set(false);
          toast.add({ severity: 'error', summary: 'Nie udało się wysłać wiadomości' });
        },
      });
    },

    openNewMessageDialog: (): void => {
      newMessageForm.reset({ recipientIds: [], subject: '', body: '' });
      newMessagePatient.set(null);
      newMessageDialogVisible.set(true);
    },

    onNewMessagePatientSelected: (patient: PatientSummary): void => {
      newMessagePatient.set(patient);
      patients.cache.set(patient.id, patient);
    },

    submitNewMessage: (): void => {
      newMessageForm.markAllAsTouched();
      if (newMessageForm.invalid) return;
      const value = newMessageForm.getRawValue();
      const participantIds = [ctx.currentUser().id, ...value.recipientIds];
      service
        .createThread(participantIds, value.subject, newMessagePatient()?.id, value.body)
        .subscribe({
          next: (thread) => {
            toast.add({ severity: 'success', summary: 'Utworzono wątek' });
            newMessageDialogVisible.set(false);
            load();
            openThreadInUrl(thread.id);
          },
          error: () => toast.add({ severity: 'error', summary: 'Nie udało się utworzyć wątku' }),
        });
    },
  };
}
