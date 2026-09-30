import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import type { LabOrderDraft } from '../models';
import { LabOrderService } from './lab-order.service';

function draft(): LabOrderDraft {
  return {
    patientId: 'pat-003',
    orderedById: 'stf-001',
    items: [{ testCode: 'MORF', testName: 'Morfologia krwi z rozmazem', specimenType: 'blood' }],
    urgency: 'routine',
    fasting: false,
    plannedCollectionAt: '2026-01-01T08:00:00.000Z',
    clinicalInfo: 'Kontrola.',
  };
}

describe('LabOrderService', () => {
  let service: LabOrderService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    service = TestBed.inject(LabOrderService);
  });

  it('getCatalog returns the lab test catalog', async () => {
    const catalog = await firstValueFrom(service.getCatalog());
    expect(catalog.length).toBeGreaterThanOrEqual(15);
  });

  it('getPanels returns lab panels', async () => {
    const panels = await firstValueFrom(service.getPanels());
    expect(panels.some((p) => p.name === 'Profil kardiologiczny')).toBe(true);
    expect(panels.every((p) => Array.isArray(p.testCodes) && p.testCodes.length > 0)).toBe(true);
  });

  it('getOrders filters by patientId and status', async () => {
    const byPatient = await firstValueFrom(service.getOrders({ patientId: 'pat-001' }));
    expect(byPatient.every((o) => o.patientId === 'pat-001')).toBe(true);

    const byStatus = await firstValueFrom(service.getOrders({ status: 'completed' }));
    expect(byStatus.every((o) => o.status === 'completed')).toBe(true);
  });

  it('createOrder sets status to ordered and appends a statusHistory entry, and persists', async () => {
    const before = await firstValueFrom(service.getOrders());
    const order = await firstValueFrom(service.createOrder(draft()));
    expect(order.status).toBe('ordered');
    expect(order.statusHistory).toHaveLength(1);
    expect(order.statusHistory[0].status).toBe('ordered');

    const after = await firstValueFrom(service.getOrders());
    expect(after.length).toBe(before.length + 1);
  });

  it('updateStatus appends to statusHistory', async () => {
    const updated = await firstValueFrom(
      service.updateStatus('lord-006', 'specimen_collected', 'ok'),
    );
    expect(updated.status).toBe('specimen_collected');
    expect(updated.statusHistory.at(-1)).toMatchObject({
      status: 'specimen_collected',
      note: 'ok',
    });
  });

  it('cancelOrder sets status to cancelled', async () => {
    const updated = await firstValueFrom(service.cancelOrder('lord-006', 'Pacjent zrezygnował'));
    expect(updated.status).toBe('cancelled');
  });
});
