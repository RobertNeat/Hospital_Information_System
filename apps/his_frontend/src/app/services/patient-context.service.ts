import { Injectable, computed, inject, signal } from '@angular/core';
import { tap } from 'rxjs';
import type { Observable } from 'rxjs';
import type { Patient, PatientSummary } from '../models';
import { toPatientSummary } from '../utils/patient-summary';
import { PatientService } from './patient.service';
import { WardService } from './ward.service';

const SESSION_KEY = 'his.currentPatientId';
const MAX_RECENT = 5;

/**
 * Pure client-side state (not a backend contract).
 * Derived cache of the `patients/:patientId` route param. The route param remains
 * the source of truth (so deep links and refreshes work); this service just holds
 * the currently-resolved patient for display and for global/patient-scoped links.
 */
@Injectable({ providedIn: 'root' })
export class PatientContextService {
  private readonly patientService = inject(PatientService);
  private readonly wardService = inject(WardService);

  private readonly _patient = signal<Patient | null>(null);
  readonly patient = this._patient.asReadonly();
  readonly patientId = computed(() => this._patient()?.id ?? null);
  readonly hasPatient = computed(() => !!this._patient());

  private readonly recent = signal<Patient[]>([]);
  /** Recently opened patients; ward names follow the ward dictionary as it loads. */
  readonly recentPatients = computed<PatientSummary[]>(() =>
    this.recent().map((p) => toPatientSummary(p, this.wardService.wards())),
  );

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
    this.recent.update((list) => [p, ...list.filter((r) => r.id !== p.id)].slice(0, MAX_RECENT));
    if (p.status === 'admitted') this.wardService.load().subscribe({ error: () => undefined });
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
