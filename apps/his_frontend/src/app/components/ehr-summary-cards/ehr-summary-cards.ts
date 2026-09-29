import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import type { Diagnosis, EhrSummary, Encounter } from '../../models';
import { LabelPipe } from '../../pipes/label.pipe';
import { StaffNamePipe } from '../../pipes/staff-name.pipe';
import { SectionHeader } from '../section-header/section-header';
import { EmptyState } from '../empty-state/empty-state';

/**
 * "Przegląd" tab of the EHR: four summary cards (recent diagnoses, active
 * medications, chronic conditions, recent encounters) plus an optional
 * "Wizyta w trakcie" card when an encounter has status `in_progress`.
 */
@Component({
  selector: 'app-ehr-summary-cards',
  imports: [DatePipe, LabelPipe, StaffNamePipe, SectionHeader, EmptyState],
  templateUrl: './ehr-summary-cards.html',
  styleUrl: './ehr-summary-cards.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EhrSummaryCards {
  readonly summary = input.required<EhrSummary>();
  readonly encounters = input<Encounter[]>([]);

  protected readonly inProgressEncounter = computed(() =>
    this.encounters().find((e) => e.status === 'in_progress'),
  );

  protected trackDiagnosis(_index: number, d: Diagnosis): string {
    return d.id;
  }

  protected trackEncounter(_index: number, e: Encounter): string {
    return e.id;
  }
}
