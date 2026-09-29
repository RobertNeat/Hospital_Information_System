import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Tag } from 'primeng/tag';
import type { TagSeverity } from '../../models';

export interface SummaryItem {
  label: string;
  value: string | null;
  hint?: string;
  severity?: TagSeverity;
}

@Component({
  selector: 'app-summary-list',
  imports: [Tag],
  templateUrl: './summary-list.html',
  styleUrl: './summary-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SummaryList {
  readonly items = input.required<SummaryItem[]>();
  readonly columns = input<1 | 2>(2);
}
