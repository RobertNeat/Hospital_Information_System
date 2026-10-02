import { computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { HandoffNoteDialogResult } from '../../components/handoff-note-dialog/handoff-note-dialog';
import type { ClinicalAlert, HandoffNote } from '../../models';
import { TeamMessageService } from '../../services/team-message.service';
import { WardService } from '../../services/ward.service';
import { minutesAgo } from '../../utils/date-utils';
import type { MessagesContext } from './messages.context';

/** "Przekazanie dyżuru" tab. Injection context only. */
export function createHandoffState(ctx: MessagesContext) {
  const service = inject(TeamMessageService);
  const wardService = inject(WardService);

  const handoffNotes = signal<HandoffNote[]>([]);
  const handoffLoading = signal(true);
  const handoffDialogVisible = signal(false);

  const load = (): void => {
    handoffLoading.set(true);
    service.getHandoffNotes().subscribe({
      next: (notes) => {
        handoffNotes.set(notes);
        handoffLoading.set(false);
        for (const n of notes) {
          for (const p of n.patientNotes) ctx.patients.resolve(p.patientId);
        }
      },
      // Naprawiony blad: brak obslugi bledu zostawial `handoffLoading` na `true` na zawsze.
      error: () => {
        handoffLoading.set(false);
        ctx.toast.add({ severity: 'error', summary: 'Nie udało się wczytać przekazań dyżuru' });
      },
    });
  };

  return {
    handoffNotes,
    handoffLoading,
    handoffDialogVisible,
    load,
    wardName: (wardId: string): string => wardService.nameOf(wardId),

    onCreateHandoffNote: (result: HandoffNoteDialogResult): void => {
      service
        .createHandoffNote({
          wardId: result.wardId,
          shiftDate: new Date().toISOString().slice(0, 10),
          shift: result.shift,
          toId: result.toId,
          generalNotes: result.generalNotes,
          patientNotes: result.patientNotes,
        })
        .subscribe({
          next: () => {
            ctx.toast.add({ severity: 'success', summary: 'Przekazanie dyżuru zapisane' });
            load();
          },
          error: () =>
            ctx.toast.add({ severity: 'error', summary: 'Nie udało się zapisać przekazania' }),
        });
    },
  };
}

const SEVERITY_ORDER: Record<ClinicalAlert['severity'], number> = {
  critical: 0,
  warning: 1,
  info: 2,
};

/** "Alerty" tab. Injection context only. */
export function createAlertsState(ctx: MessagesContext) {
  const service = inject(TeamMessageService);

  const alerts = signal<ClinicalAlert[]>([]);
  const alertsLoading = signal(true);
  const sortedAlerts = computed(() =>
    [...alerts()].sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity]),
  );

  const load = (silent = false): void => {
    if (!silent) alertsLoading.set(true);
    service.getAlerts().subscribe({
      next: (list) => {
        alerts.set(list);
        alertsLoading.set(false);
        for (const a of list) {
          if (a.patientId) ctx.patients.resolve(a.patientId);
        }
      },
      // Naprawiony blad: brak obslugi bledu zostawial `alertsLoading` na `true` na zawsze.
      error: () => {
        alertsLoading.set(false);
        ctx.toast.add({ severity: 'error', summary: 'Nie udało się wczytać alertów' });
      },
    });
  };

  service.pushed$.pipe(takeUntilDestroyed()).subscribe((push) => {
    if (push.kind === 'alert' || push.kind === 'resync') load(true);
  });

  return {
    alertsLoading,
    sortedAlerts,
    load,

    acknowledgeAlert: (alert: ClinicalAlert): void => {
      service.acknowledgeAlert(alert.id).subscribe({
        next: () => {
          load();
          ctx.toast.add({ severity: 'success', summary: 'Alert potwierdzony' });
        },
        error: () =>
          ctx.toast.add({ severity: 'error', summary: 'Nie udało się potwierdzić alertu' }),
      });
    },

    alertAgo: (alert: ClinicalAlert): string => {
      const mins = minutesAgo(alert.createdAt);
      if (mins < 60) return `${mins} min temu`;
      return `${Math.round(mins / 60)} godz. temu`;
    },
  };
}
