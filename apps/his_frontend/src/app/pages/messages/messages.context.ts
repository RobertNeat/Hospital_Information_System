import { signal, type Signal } from '@angular/core';
import type { Router } from '@angular/router';
import type { MessageService as ToastService } from 'primeng/api';
import type { PatientSummary, StaffMember } from '../../models';
import type { PatientService } from '../../services/patient.service';

export interface PatientCache {
  readonly cache: Map<string, PatientSummary>;
  /** Bumped whenever a patient gets resolved, so dependent computeds re-evaluate. */
  readonly tick: Signal<number>;
  label: (patientId: string) => string;
  resolve: (id: string) => void;
}

export function createPatientCache(patientService: PatientService): PatientCache {
  const cache = new Map<string, PatientSummary>();
  const tick = signal(0);
  return {
    cache,
    tick: tick.asReadonly(),
    label: (patientId) => {
      const cached = cache.get(patientId);
      return cached ? `${cached.lastName} ${cached.firstName}` : patientId;
    },
    resolve: (id) => {
      if (cache.has(id)) return;
      patientService.getPatientById(id).subscribe({
        next: (p) => {
          cache.set(id, {
            id: p.id,
            mrn: p.mrn,
            pesel: p.pesel,
            firstName: p.firstName,
            lastName: p.lastName,
            birthDate: p.birthDate,
            gender: p.gender,
            status: p.status,
            flags: p.flags,
          });
          tick.update((n) => n + 1);
        },
        error: () => undefined,
      });
    },
  };
}

/** Shared dependencies of the per-tab state factories (all created in an injection context). */
export interface MessagesContext {
  currentUser: Signal<StaffMember>;
  toast: ToastService;
  router: Router;
  patients: PatientCache;
}
