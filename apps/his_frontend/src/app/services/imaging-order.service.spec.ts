import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import type { ImagingOrderDraft } from '../models';
import { ImagingOrderService } from './imaging-order.service';

function draft(slotId?: string): ImagingOrderDraft {
  return {
    patientId: 'pat-003',
    examCode: 'USG-JB',
    examName: 'USG jamy brzusznej',
    modality: 'USG',
    bodyRegion: 'Jama brzuszna',
    laterality: 'na',
    contrast: false,
    clinicalIndication: 'Test',
    urgency: 'routine',
    safety: {
      pregnancy: 'na',
      pacemakerOrImplant: false,
      metalFragments: false,
      contrastAllergy: false,
      claustrophobia: false,
      confirmed: true,
    },
    slotId,
    orderedById: 'stf-001',
  };
}

describe('ImagingOrderService', () => {
  let service: ImagingOrderService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    service = TestBed.inject(ImagingOrderService);
  });

  it('getCatalog filters by modality', async () => {
    const catalog = await firstValueFrom(service.getCatalog('CT'));
    expect(catalog.every((e) => e.modality === 'CT')).toBe(true);
  });

  it('getSlots generates 8:00-16:00 half-hour slots deterministically', async () => {
    const slots1 = await firstValueFrom(service.getSlots('USG', '2026-06-01'));
    const slots2 = await firstValueFrom(service.getSlots('USG', '2026-06-01'));
    expect(slots1.length).toBeGreaterThan(0);
    expect(slots1).toEqual(slots2);
    expect(slots1.some((s) => !s.available)).toBe(true);
  });

  it('createOrder without a slot sets status to ordered', async () => {
    const order = await firstValueFrom(service.createOrder(draft()));
    expect(order.status).toBe('ordered');
  });

  it('createOrder with a slot sets status to scheduled, and persists', async () => {
    const before = await firstValueFrom(service.getOrders());
    const order = await firstValueFrom(service.createOrder(draft('slot-test-1')));
    expect(order.status).toBe('scheduled');

    const after = await firstValueFrom(service.getOrders());
    expect(after.length).toBe(before.length + 1);
  });

  it('cancelOrder sets status to cancelled', async () => {
    const updated = await firstValueFrom(service.cancelOrder('iord-005', 'Pacjent zrezygnował'));
    expect(updated.status).toBe('cancelled');
  });
});
