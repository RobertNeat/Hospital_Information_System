import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { HandoffNote } from '../../models';
import { StaffNamePipe } from '../../pipes/staff-name.pipe';

@Component({
  selector: 'app-messages-handoff-list',
  imports: [DatePipe, StaffNamePipe],
  templateUrl: './messages-handoff-list.html',
  styleUrl: './messages-handoff-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'messages-handoff-list' },
})
export class MessagesHandoffList {
  readonly notes = input.required<HandoffNote[]>();
  readonly wardName = input.required<(wardId: string) => string>();
  readonly patientLabel = input.required<(patientId: string) => string>();
}
