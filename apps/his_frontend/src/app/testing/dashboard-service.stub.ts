import { of } from 'rxjs';
import type { DashboardStats } from '../models';
import { DashboardService } from '../services/dashboard.service';

export const DASHBOARD_STATS_FIXTURE: DashboardStats = {
  admittedPatients: 6,
  newResults: 4,
  criticalAlerts: 2,
  openTasks: 3,
  pendingOrders: 5,
  vitalsAnomalies: 1,
};

/** Test provider replacing the HTTP-backed `DashboardService` with fixed counters. */
export const dashboardServiceStub = {
  provide: DashboardService,
  useValue: { getStats: () => of(DASHBOARD_STATS_FIXTURE) },
};
