import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import type { Signal } from '@angular/core';
import { finalize, of, shareReplay, tap } from 'rxjs';
import type { Observable } from 'rxjs';
import { STAFF_URL, staffActivateUrl, staffLockUrl, staffUrl } from '../config/api.config';
import type { StaffMember, StaffRole } from '../models';
import type { CurrentUser } from '../models/api';
import { toHttpParams } from '../utils/http-params';
import { AuthService } from './auth.service';

/**
 * Staff directory backed by `GET /staff` plus the signed-in user from `AuthService`.
 *
 * The unfiltered list is cached in a signal (`load()`); filtered queries hit the API and
 * their results are merged into the lookup used by `nameOf()`. `nameOf()` is synchronous
 * and, while nothing is cached and the user is signed in, triggers one background load
 * (never retried after a failure, e.g. 403 without `staff:read`).
 */
@Injectable({ providedIn: 'root' })
export class StaffService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private readonly cache = signal<StaffMember[] | null>(null);
  private readonly known = signal<ReadonlyMap<string, StaffMember>>(new Map());
  private inflight: Observable<StaffMember[]> | undefined;
  private loadFailed = false;

  /**
   * The signed-in user from `AuthService`. Routes are guarded, so a session exists whenever
   * this is read; the last known user is kept while the session is being torn down on logout
   * (avoids a throw during the final change detection), and reading it before any sign-in throws.
   */
  readonly currentUser: Signal<CurrentUser> = computed(() => {
    const user = this.auth.currentUser() ?? this.lastUser;
    if (!user) throw new Error('Brak zalogowanego użytkownika');
    this.lastUser = user;
    return user;
  });
  private lastUser: CurrentUser | null = null;

  /** Cached staff (empty until loaded). */
  readonly staff = computed(() => this.cache() ?? []);

  /** Emits the cached full list, fetching it first when missing (or when `force`). */
  load(force = false): Observable<StaffMember[]> {
    const cached = this.cache();
    if (cached && !force) return of(cached);
    this.inflight ??= this.http.get<StaffMember[]>(STAFF_URL).pipe(
      tap({
        next: (staff) => {
          this.cache.set(staff);
          this.remember(staff);
        },
        error: () => (this.loadFailed = true),
      }),
      finalize(() => (this.inflight = undefined)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.inflight;
  }

  /** Staff filtered by role and/or ward; unfiltered calls use the cache. */
  getStaff(role?: StaffRole, wardId?: string): Observable<StaffMember[]> {
    if (!role && !wardId) return this.load();
    return this.http
      .get<StaffMember[]>(STAFF_URL, { params: toHttpParams({ role, wardId }) })
      .pipe(tap((staff) => this.remember(staff)));
  }

  getById(id: string): Observable<StaffMember> {
    return this.http.get<StaffMember>(staffUrl(id)).pipe(tap((member) => this.remember([member])));
  }

  /** Activates (or unlocks) the account; requires `account:manage`. No request body. */
  activate(staffId: string): Observable<StaffMember> {
    return this.http
      .post<StaffMember>(staffActivateUrl(staffId), null)
      .pipe(tap((member) => this.remember([member])));
  }

  /** Locks the account; requires `account:manage`. The backend rejects locking one's own account (409). */
  lock(staffId: string): Observable<StaffMember> {
    return this.http
      .post<StaffMember>(staffLockUrl(staffId), null)
      .pipe(tap((member) => this.remember([member])));
  }

  /** Synchronous lookup for pipes/templates; falls back to the id. */
  nameOf(id: string): string {
    if (this.cache() === null && !this.loadFailed && this.auth.isAuthenticated())
      this.load().subscribe({ error: () => undefined });
    const s = this.known().get(id);
    return s ? `${s.title} ${s.firstName} ${s.lastName}` : id;
  }

  /** Drops cached data (e.g. on logout). */
  clear(): void {
    this.cache.set(null);
    this.known.set(new Map());
    this.loadFailed = false;
    this.lastUser = null;
  }

  private remember(staff: StaffMember[]): void {
    this.known.update((m) => new Map([...m, ...staff.map((s) => [s.id, s] as const)]));
    const updated = new Map(staff.map((s) => [s.id, s] as const));
    this.cache.update((cached) => (cached ? cached.map((s) => updated.get(s.id) ?? s) : cached));
  }
}
