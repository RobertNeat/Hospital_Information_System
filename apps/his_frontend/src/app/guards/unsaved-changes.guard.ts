import { inject } from '@angular/core';
import { ConfirmationService } from 'primeng/api';
import { Observable } from 'rxjs';
import type { CanDeactivateFn } from '@angular/router';

/** Implemented by wizard pages so `unsavedChangesGuard` can ask before navigating away. */
export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

/**
 * Blocks navigation away from a component with unsaved changes, confirming via
 * PrimeNG's `ConfirmationService` (rendered by `<p-confirmdialog>` in AppShell,
 * Phase 0b's job). Returns `true` immediately when there is nothing to lose.
 */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component) => {
  if (!component.hasUnsavedChanges()) {
    return true;
  }

  const confirmationService = inject(ConfirmationService);

  return new Observable<boolean>((subscriber) => {
    confirmationService.confirm({
      header: 'Niezapisane zmiany',
      message: 'Masz niezapisane zmiany. Czy na pewno chcesz opuścić tę stronę?',
      acceptLabel: 'Tak, opuść',
      rejectLabel: 'Anuluj',
      accept: () => {
        subscriber.next(true);
        subscriber.complete();
      },
      reject: () => {
        subscriber.next(false);
        subscriber.complete();
      },
    });
  });
};
