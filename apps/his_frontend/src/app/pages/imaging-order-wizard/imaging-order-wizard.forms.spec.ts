import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { findNearestSlot } from './imaging-order-wizard.forms';
import type { ScheduleSlot } from '../../models';

const SLOT: ScheduleSlot = {
  id: 'slot-1',
  modality: 'RTG',
  room: 'Sala 1',
  start: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
  end: new Date(Date.now() + 90 * 60 * 1000).toISOString(),
  available: true,
};

describe('findNearestSlot', () => {
  it('calls `found` with the first available slot on the first day that has one', () => {
    const found = vi.fn();
    const onError = vi.fn();
    findNearestSlot(() => of([SLOT]), 'RTG', found, onError);
    expect(found).toHaveBeenCalledWith(expect.any(Date), SLOT);
    expect(onError).not.toHaveBeenCalled();
  });

  it('calls `onError` once and never calls `found` when a day lookup fails, instead of silently stopping', () => {
    const found = vi.fn();
    const onError = vi.fn();
    findNearestSlot(() => throwError(() => new Error('500')), 'RTG', found, onError);
    expect(found).not.toHaveBeenCalled();
    expect(onError).toHaveBeenCalledTimes(1);
  });
});
