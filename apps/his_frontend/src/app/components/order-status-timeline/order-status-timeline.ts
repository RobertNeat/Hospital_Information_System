import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Timeline } from 'primeng/timeline';
import type { StatusChange } from '../../models';
import { StaffNamePipe } from '../../pipes/staff-name.pipe';
import { StatusTag } from '../status-tag/status-tag';

/** `p-timeline` view of a lab/imaging order's `statusHistory`, newest first. */
@Component({
  selector: 'app-order-status-timeline',
  imports: [Timeline, DatePipe, StaffNamePipe, StatusTag],
  templateUrl: './order-status-timeline.html',
  styleUrl: './order-status-timeline.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderStatusTimeline {
  readonly history = input.required<StatusChange[]>();

  protected readonly entries = computed<StatusChange[]>(() =>
    [...this.history()].sort((a, b) => b.at.localeCompare(a.at)),
  );
}
