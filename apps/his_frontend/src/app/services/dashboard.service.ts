import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import { DASHBOARD_STATS_URL } from '../config/api.config';
import type { DashboardStats } from '../models';

/** Counters computed by the backend for the signed-in user (`GET /dashboard/stats`). */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  getStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(DASHBOARD_STATS_URL);
  }
}
