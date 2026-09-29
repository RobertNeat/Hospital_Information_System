import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  let service: DashboardService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    service = TestBed.inject(DashboardService);
  });

  it('combines stats from multiple services', async () => {
    const stats = await firstValueFrom(service.getStats());
    expect(stats.admittedPatients).toBeGreaterThan(0);
    expect(typeof stats.newResults).toBe('number');
    expect(typeof stats.criticalAlerts).toBe('number');
    expect(typeof stats.openTasks).toBe('number');
    expect(typeof stats.pendingOrders).toBe('number');
    expect(typeof stats.vitalsAnomalies).toBe('number');
  });
});
