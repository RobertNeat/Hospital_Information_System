import { computed, inject, signal, type Signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
    service.markThreadRead(threadId).subscribe({
      next: (updated) =>
        threads.update((list) => list.map((t) => (t.id === updated.id ? updated : t))),
      error: () => undefined,
    });
  };

  const openThreadInUrl = (threadId: string): void => {
    void router.navigate([], {
      queryParams: { tab: 'inbox', thread: threadId },
      queryParamsHandling: 'merge',
    });
  };

  const reloadThreads = (): void => {
    service.getThreads().subscribe({
      next: (list) => {
        threads.set(list);
        for (const t of list) {
          if (t.patientId) patients.resolve(t.patientId);
        }
      },
      error: () => undefined,
    });
  };

  // Push: keep the open conversation and the thread list current without resetting the selection.
  service.pushed$.pipe(takeUntilDestroyed()).subscribe((push) => {
    if (push.kind === 'thread') {
      threads.update((list) =>
        list.some((t) => t.id === push.thread.id)
          ? list.map((t) => (t.id === push.thread.id ? push.thread : t))
          : [push.thread, ...list],
      );
    } else if (push.kind === 'message' && push.message.threadId === selectedThreadId()) {
      reloadMessages(push.message.threadId);
      service.markThreadRead(push.message.threadId).subscribe({
        next: (updated) =>
          threads.update((list) => list.map((t) => (t.id === updated.id ? updated : t))),
        error: () => undefined,
      });
    } else if (push.kind === 'resync') {
      reloadThreads();
      const open = selectedThreadId();
      if (open) reloadMessages(open);
    }
  });

  const load = (): void => {
    threadsLoading.set(true);
    service.getThreads().subscribe({
      next: (list) => {
        threads.set(list);
        threadsLoading.set(false);
        for (const t of list) {
          if (t.patientId) patients.resolve(t.patientId);
        }
        const wanted = preselectedThread();
        if (wanted && !list.some((t) => t.id === wanted)) {
          // A thread outside the list (e.g. a deep link): fetch it; 403/404 fall back to the first.
          service.getThread(wanted).subscribe({
            next: (thread) => {
              threads.update((current) => [thread, ...current]);
              selectThread(thread.id);
            },
            error: () => {
              toast.add({ severity: 'warn', summary: 'Wątek jest niedostępny' });
              if (list[0]) selectThread(list[0].id);
            },
          });
          return;
        }
        const preselect = wanted ?? list[0]?.id ?? null;
        if (preselect) selectThread(preselect);
      },
      error: () => {
        threadsLoading.set(false);
        toast.add({ severity: 'error', summary: 'Nie udało się pobrać wątków' });
      },
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
