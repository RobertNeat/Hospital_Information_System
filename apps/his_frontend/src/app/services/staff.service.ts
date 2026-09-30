import { Injectable, inject, signal } from '@angular/core';
import type { Signal } from '@angular/core';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { STAFF } from '../mock-data/staff.mock';
import type { StaffMember, StaffRole } from '../models';
import type { CurrentUser } from '../models/api';
import { mockError, mockResponse } from '../utils/mock-response';

@Injectable({ providedIn: 'root' })
export class StaffService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly staff: StaffMember[] = structuredClone(STAFF);

  private readonly _currentUser = signal<CurrentUser>(
    this.staff.find((s) => s.id === 'stf-001') ?? this.staff[0],
  );
  /** "lek. Anna Nowak" (stf-001), the demo's logged-in user. */
  readonly currentUser: Signal<CurrentUser> = this._currentUser.asReadonly();

  getStaff(role?: StaffRole): Observable<StaffMember[]> {
    const result = role ? this.staff.filter((s) => s.role === role) : this.staff;
    return mockResponse(result, this.latency);
  }

  getById(id: string): Observable<StaffMember> {
    const found = this.staff.find((s) => s.id === id);
    if (!found) return mockError(`Nie znaleziono pracownika o id ${id}`, this.latency);
    return mockResponse(found, this.latency);
  }

  /** Synchronous lookup from the in-memory cache, used by `staff-name.pipe`. */
  nameOf(id: string): string {
    const s = this.staff.find((m) => m.id === id);
    return s ? `${s.title} ${s.firstName} ${s.lastName}` : id;
  }
}
