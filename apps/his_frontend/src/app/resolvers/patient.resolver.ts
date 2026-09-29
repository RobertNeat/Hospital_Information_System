import { inject } from '@angular/core';
import { MessageService } from 'primeng/api';
import { RedirectCommand, Router, type ResolveFn } from '@angular/router';
import { catchError, of, tap } from 'rxjs';
import type { Patient } from '../models';
import { PatientContextService } from '../services/patient-context.service';
import { PatientService } from '../services/patient.service';

/**
 * Resolves the patient for `patients/:patientId` and syncs `PatientContextService`.
 * On error (patient not found), shows a toast and redirects to `/patients`.
 */
export const patientResolver: ResolveFn<Patient> = (route) => {
  const patientService = inject(PatientService);
  const ctx = inject(PatientContextService);
  const messageService = inject(MessageService);
  const router = inject(Router);

  const patientId = route.paramMap.get('patientId');
  if (!patientId) {
    return new RedirectCommand(router.parseUrl('/patients'));
  }

  return patientService.getPatientById(patientId).pipe(
    tap((patient) => ctx.setPatient(patient)),
    catchError(() => {
      messageService.add({ severity: 'error', summary: 'Nie znaleziono pacjenta' });
      return of(new RedirectCommand(router.parseUrl('/patients')));
    }),
  );
};
