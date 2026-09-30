import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Message } from 'primeng/message';
import type { Allergy, Contraindication } from '../../models';
import { LabelPipe } from '../../pipes/label.pipe';
import { EmptyState } from '../empty-state/empty-state';
import { SectionHeader } from '../section-header/section-header';
import { StatusTag } from '../status-tag/status-tag';

@Component({
  selector: 'app-history-allergies-panel',
  imports: [Message, EmptyState, SectionHeader, StatusTag, DatePipe, LabelPipe],
  templateUrl: './history-allergies-panel.html',
  styleUrl: './history-allergies-panel.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'history-allergies-panel' },
})
export class HistoryAllergiesPanel {
  readonly allergies = input.required<Allergy[]>();
  readonly contraindications = input.required<Contraindication[]>();
  readonly lifeThreatening = input.required<Allergy[]>();
}
