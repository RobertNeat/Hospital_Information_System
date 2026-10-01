import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { imagingOrderServiceStub } from '../../testing/imaging-order-service.stub';
import { SlotPicker } from './slot-picker';
import type { ScheduleSlot } from '../../models';

describe('SlotPicker', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), imagingOrderServiceStub],
    });
  });

  it('fetches and groups slots by room for the given modality and date', async () => {
    const fixture = TestBed.createComponent(SlotPicker);
    fixture.componentRef.setInput('modality', 'USG');
    fixture.componentRef.setInput('date', '2026-10-05');
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('USG-1');
  });

  it('emits slotSelected only when clicking an available slot button', async () => {
    const fixture = TestBed.createComponent(SlotPicker);
    fixture.componentRef.setInput('modality', 'USG');
    fixture.componentRef.setInput('date', '2026-10-05');
    await fixture.whenStable();

    let emitted: ScheduleSlot | null = null;
    fixture.componentInstance.slotSelected.subscribe((s) => (emitted = s));

    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll(
      'p-button button:not([disabled])',
    );
    expect(buttons.length).toBeGreaterThan(0);

    (buttons[0] as HTMLElement).click();
    await fixture.whenStable();
    expect(emitted).not.toBeNull();
  });
});
