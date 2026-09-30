import type { WritableSignal } from '@angular/core';
import type { Router } from '@angular/router';
import type { MessageService } from 'primeng/api';

export interface OrderSubmitContext {
  submitting: WritableSignal<boolean>;
  submitted: WritableSignal<boolean>;
  toast: MessageService;
  router: Router;
  patientId: string;
  /** Value of the `type` query param on the patient orders page. */
  orderType: 'lab' | 'imaging';
  isCito: boolean;
  successDetail: string;
}

/** Shared subscribe-observer for order wizards: toasts + redirect to the patient's orders list. */
export function orderSubmitObserver(ctx: OrderSubmitContext) {
  return {
    next: () => {
      ctx.submitting.set(false);
      ctx.submitted.set(true);
      ctx.toast.add({
        severity: 'success',
        summary: ctx.isCito ? 'Zlecenie CITO wysłane' : 'Zlecenie wysłane',
        detail: ctx.successDetail,
      });
      void ctx.router.navigate(['/patients', ctx.patientId, 'orders'], {
        queryParams: { type: ctx.orderType },
      });
    },
    error: () => {
      ctx.submitting.set(false);
      ctx.toast.add({ severity: 'error', summary: 'Nie udało się wysłać zlecenia' });
    },
  };
}

export function warnIncompleteOrder(toast: MessageService): void {
  toast.add({
    severity: 'warn',
    summary: 'Uzupełnij wymagane pola',
    detail: 'Sprawdź wszystkie kroki formularza przed wysłaniem zlecenia.',
  });
}
