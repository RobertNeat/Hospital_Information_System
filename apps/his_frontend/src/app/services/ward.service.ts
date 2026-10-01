import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { finalize, of, shareReplay, tap } from 'rxjs';
import type { Observable } from 'rxjs';
import { WARDS_URL } from '../config/api.config';
import type { Ward } from '../models';
import { AuthService } from './auth.service';

/**
 * Ward dictionary backed by `GET /wards`, cached in a signal.
 *
 * Loading is lazy: `getWards()`/`load()` fetch once and reuse the cache; `nameOf()` is
 * synchronous and, while the cache is empty and the user is signed in, triggers one load
 * (the signal read makes templates refresh when it arrives; never retried after a failure).
 */
@Injectable({ providedIn: 'root' })
export class WardService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private readonly cache = signal<Ward[] | null>(null);
  private readonly byId = computed(() => new Map((this.cache() ?? []).map((w) => [w.id, w])));
  private inflight: Observable<Ward[]> | undefined;
  private loadFailed = false;

  /** Cached wards (empty until loaded). */
  readonly wards = computed(() => this.cache() ?? []);

  /** Emits the cached list, fetching it first when missing (or when `force`). */
  load(force = false): Observable<Ward[]> {
    const cached = this.cache();
    if (cached && !force) return of(cached);
    this.inflight ??= this.http.get<Ward[]>(WARDS_URL).pipe(
      tap({
        next: (wards) => this.cache.set(wards),
        error: () => (this.loadFailed = true),
      }),
      finalize(() => (this.inflight = undefined)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.inflight;
  }

  getWards(): Observable<Ward[]> {
    return this.load();
  }

  /** Synchronous lookup from the cache, for pipes/templates; falls back to the id. */
  nameOf(id: string): string {
    if (this.cache() === null && !this.loadFailed && this.auth.isAuthenticated())
      this.load().subscribe({ error: () => undefined });
    return this.byId().get(id)?.name ?? id;
  }

  /** Drops the cache (e.g. on logout). */
  clear(): void {
    this.cache.set(null);
    this.loadFailed = false;
  }
}
