import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap } from 'rxjs';
import { Button } from 'primeng/button';
import type { ImagingModality, ISODate, ScheduleSlot } from '../../models';
import { ImagingOrderService } from '../../services/imaging-order.service';

/**
 * Button grid of a day's schedule slots for one modality, grouped by room. Fetches
 * slots itself from `ImagingOrderService.getSlots(modality, date)` whenever the
 * `modality`/`date` inputs change.
 */
@Component({
  selector: 'app-slot-picker',
  imports: [Button, DatePipe],
  templateUrl: './slot-picker.html',
  styleUrl: './slot-picker.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'slot-picker' },
})
export class SlotPicker {
  private readonly imagingOrderService = inject(ImagingOrderService);

  readonly modality = input.required<ImagingModality>();
  readonly date = input.required<ISODate>();
  readonly selectedSlotId = input<string | null>(null);
  /** Change to refetch the slots (e.g. after a 409 on a slot taken in the meantime). */
  readonly refreshKey = input(0);

  readonly slotSelected = output<ScheduleSlot>();

  private readonly params = computed(() => ({
    modality: this.modality(),
    date: this.date(),
    refresh: this.refreshKey(),
  }));

  private readonly slotsSignal = toSignal(
    toObservable(this.params).pipe(
      switchMap(({ modality, date }) =>
        this.imagingOrderService.getSlots(modality, date).pipe(catchError(() => of([]))),
      ),
    ),
    { initialValue: undefined },
  );

  protected readonly loading = computed(() => this.slotsSignal() === undefined);

  protected readonly rooms = computed(() => {
    const slots = this.slotsSignal() ?? [];
    const byRoom = new Map<string, ScheduleSlot[]>();
    for (const slot of slots) {
      const bucket = byRoom.get(slot.room);
      if (bucket) bucket.push(slot);
      else byRoom.set(slot.room, [slot]);
    }
    return Array.from(byRoom.entries())
      .map(([room, roomSlots]) => ({
        room,
        slots: [...roomSlots].sort((a, b) => a.start.localeCompare(b.start)),
      }))
      .sort((a, b) => a.room.localeCompare(b.room));
  });

  protected readonly hasSlots = computed(() => (this.slotsSignal()?.length ?? 0) > 0);

  protected select(slot: ScheduleSlot): void {
    if (!slot.available) return;
    this.slotSelected.emit(slot);
  }
}
