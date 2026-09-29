import { inject } from '@angular/core';
import { Router, type RedirectFunction } from '@angular/router';
import { PatientContextService } from '../services/patient-context.service';

/**
 * Builds a `RedirectFunction` for a global entry-point route (e.g. `orders/lab/new`)
 * that actually lives nested under a patient, at `/patients/:patientId/<next>`.
 *
 * Runs in an injection context (Angular calls `RedirectFunction`s that way), so it
 * may `inject()` directly. If a patient is currently in context it redirects into the
 * nested route; otherwise it sends the user to the patient picker with `next` so the
 * picker can continue the original navigation once a patient is chosen.
 */
export function patientScopedRedirect(next: string): RedirectFunction {
  return () => {
    const ctx = inject(PatientContextService);
    const router = inject(Router);
    const patientId = ctx.patientId();
    if (patientId) {
      return `/patients/${patientId}/${next}`;
    }
    return router.createUrlTree(['/select-patient'], { queryParams: { next } });
  };
}
