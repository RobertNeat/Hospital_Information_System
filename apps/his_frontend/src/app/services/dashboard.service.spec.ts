import { labOrderServiceStub } from '../testing/lab-order-service.stub';
import { labResultServiceStub } from '../testing/lab-result-service.stub';
import { patientServiceStub } from '../testing/patient-service.stub';
import { ehrServiceStub } from '../testing/ehr-service.stub';
import { wardServiceStub } from '../testing/ward-service.stub';
import { staffServiceStub } from '../testing/staff-service.stub';
import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { DashboardService } from './dashboard.service';
import { ImagingOrderService } from './imaging-order.service';
import { LabOrderService } from './lab-order.service';

describe('DashboardService', () => {
  let service: DashboardService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        patientServiceStub,
        ehrServiceStub,
        wardServiceStub,
        staffServiceStub,
        labOrderServiceStub,
        labResultServiceStub,
        { provide: MOCK_LATENCY_MS, useValue: 0 },
      ],
    });
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

  it('counts in-flight lab and imaging orders as pending', async () => {
    const lab = TestBed.inject(LabOrderService);
    const imaging = TestBed.inject(ImagingOrderService);
    const inFlight = ['ordered', 'scheduled', 'specimen_collected', 'in_progress'];
    const orders = [
      ...(await firstValueFrom(lab.getOrders())),
      ...(await firstValueFrom(imaging.getOrders())),
    ];
    const expected = orders.filter((o) => inFlight.includes(o.status)).length;
    const stats = await firstValueFrom(service.getStats());
    expect(stats.pendingOrders).toBe(expected);
  });
});
