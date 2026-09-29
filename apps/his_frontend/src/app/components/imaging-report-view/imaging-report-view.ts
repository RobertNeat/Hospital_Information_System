import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Message } from 'primeng/message';
import { SectionHeader } from '../section-header/section-header';
import type { ImagingResult } from '../../models';

/**
 * Renders an imaging report as Technika / Opis / Wnioski sections plus the
 * radiologist name, performed/reported dates, and a PACS-integration placeholder.
 */
@Component({
  selector: 'app-imaging-report-view',
  imports: [DatePipe, Message, SectionHeader],
  templateUrl: './imaging-report-view.html',
  styleUrl: './imaging-report-view.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImagingReportView {
  readonly result = input.required<ImagingResult>();
}
