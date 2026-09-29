import { Injectable, computed, inject, signal } from '@angular/core';
import { tap } from 'rxjs';
import type { Observable } from 'rxjs';
import type { Patient, PatientSummary } from '../models';
import { PatientService } from './patient.service';

const SESSION_KEY = 'his.currentPatientId';
const MAX_RECENT = 5;

function toSummary(p: Patient): PatientSummary {
  return {
    id: p.id,
    mrn: p.mrn,
    pesel: p.pesel,
    firstName: p.firstName,
    lastName: p.lastName,
    birthDate: p.birthDate,
    gender: p.gender,
    status: p.status,
    flags: p.flags,
    bed: p.currentAdmission?.bed,
  };
}

/**
 * Derived cache of the `patients/:patientId` route param. The route param remains
 * the source of truth (so deep links and refreshes work); this service just holds
 * the currently-resolved patient for display and for global/patient-scoped links.
 */
@Injectable({ providedIn: 'root' })
export class PatientContextService {
  private readonly patientService = inject(PatientService);

  private readonly _patient = signal<Patient | null>(null);
  readonly patient = this._patient.asReadonly();
  readonly patientId = computed(() => this._patient()?.id ?? null);
  readonly hasPatient = computed(() => !!this._patient());

  private readonly _recentPatients = signal<PatientSummary[]>([]);
  readonly recentPatients = this._recentPatients.asReadonly();

  constructor() {
    const storedId = this.readSessionId();
    if (storedId) {
      this.patientService.getPatientById(storedId).subscribe({
        next: (p) => this.setPatient(p),
        error: () => this.clearSession(),
      });
    }
  }

  setPatient(p: Patient): void {
    this._patient.set(p);
    const summary = toSummary(p);
    this._recentPatients.update((list) => {
      const withoutCurrent = list.filter((r) => r.id !== p.id);
      return [summary, ...withoutCurrent].slice(0, MAX_RECENT);
    });
    this.writeSessionId(p.id);
  }

  /** Reloads the current patient via `PatientService.getPatientById` and sets it. Call after an edit. */
  refresh(): Observable<Patient> {
    const id = this.patientId();
    if (!id) {
      throw new Error('PatientContextService.refresh() called with no patient in context.');
    }
    return this.patientService.getPatientById(id).pipe(tap((p) => this.setPatient(p)));
  }

  clear(): void {
    this._patient.set(null);
    this.clearSession();
  }

  private readSessionId(): string | null {
    try {
      return sessionStorage.getItem(SESSION_KEY);
    } catch {
      return null;
    }
  }

  private writeSessionId(id: string): void {
    try {
      sessionStorage.setItem(SESSION_KEY, id);
    } catch {
      /* ignore storage errors (private mode, SSR, etc.) */
    }
  }

  private clearSession(): void {
    try {
      sessionStorage.removeItem(SESSION_KEY);
    } catch {
      /* ignore */
    }
  }
}
