import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { DASHBOARD_STATS_URL } from '../config/api.config';
import { DASHBOARD_STATS_FIXTURE } from '../testing/dashboard-service.stub';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  let service: DashboardService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DashboardService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getStats GETs /dashboard/stats with a single request', async () => {
    const result = firstValueFrom(service.getStats());
    http.expectOne({ method: 'GET', url: DASHBOARD_STATS_URL }).flush(DASHBOARD_STATS_FIXTURE);
    expect(await result).toEqual(DASHBOARD_STATS_FIXTURE);
  });
});
