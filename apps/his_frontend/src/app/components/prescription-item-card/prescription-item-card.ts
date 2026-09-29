import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Button } from 'primeng/button';
import { Tag } from 'primeng/tag';
import { Tooltip } from 'primeng/tooltip';
import { LabelPipe } from '../../pipes/label.pipe';
import type { PrescriptionItem } from '../../models';

@Component({
  selector: 'app-prescription-item-card',
  imports: [Button, Tag, Tooltip, LabelPipe],
  templateUrl: './prescription-item-card.html',
  styleUrl: './prescription-item-card.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'prescription-item-card' },
})
export class PrescriptionItemCard {
  readonly item = input.required<PrescriptionItem>();
  readonly removable = input(true);

  readonly remove = output<void>();
}
