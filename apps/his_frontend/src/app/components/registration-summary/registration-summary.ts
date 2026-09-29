import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { SectionHeader } from '../section-header/section-header';
import { SummaryList, type SummaryItem } from '../summary-list/summary-list';

/** One labelled group of `SummaryItem`s rendered under its own section header. */
export interface RegistrationSummarySection {
  title: string;
  icon?: string;
  items: SummaryItem[];
}

/**
 * Review step for the patient registration/edit wizard: renders each form step's
 * values as a grouped, read-only summary built on `app-summary-list`.
 */
@Component({
  selector: 'app-registration-summary',
  imports: [SectionHeader, SummaryList],
  templateUrl: './registration-summary.html',
  styleUrl: './registration-summary.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'registration-summary' },
})
export class RegistrationSummary {
  readonly sections = input.required<RegistrationSummarySection[]>();
}
