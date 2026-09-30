import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { SectionHeader } from '../section-header/section-header';
import type { ImagingResult } from '../../models';

/**
 * Renders an imaging report as Technika / Opis / Wnioski sections plus the
 * radiologist name and performed/reported dates.
 */
@Component({
  selector: 'app-imaging-report-view',
  imports: [DatePipe, SectionHeader],
  templateUrl: './imaging-report-view.html',
  styleUrl: './imaging-report-view.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'imaging-report-view' },
})
export class ImagingReportView {
  readonly result = input.required<ImagingResult>();
}
