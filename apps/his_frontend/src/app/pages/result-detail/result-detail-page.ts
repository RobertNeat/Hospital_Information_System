import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import { PageHeader } from '../../components/page-header/page-header';
import { LoadingBlock } from '../../components/loading-block/loading-block';
import { EmptyState } from '../../components/empty-state/empty-state';
import { StatusTag } from '../../components/status-tag/status-tag';
import { LabResultObservations } from '../../components/lab-result-observations/lab-result-observations';
import { ImagingReportView } from '../../components/imaging-report-view/imaging-report-view';
import { LabResultService } from '../../services/lab-result.service';
import { ImagingResultService } from '../../services/imaging-result.service';
import { ReportDownloadService } from '../../services/report-download.service';
import { PatientContextService } from '../../services/patient-context.service';
import { LabelPipe } from '../../pipes/label.pipe';
import type { ImagingResult, LabResult } from '../../models';

type Loaded =
  | { kind: 'lab'; result: LabResult }
  | { kind: 'imaging'; result: ImagingResult }
  | { kind: 'not-found' }
  | undefined;

@Component({
  selector: 'app-result-detail-page',
  imports: [
    PageHeader,
    LoadingBlock,
    EmptyState,
    StatusTag,
    LabResultObservations,
    ImagingReportView,
    Button,
    Tooltip,
    RouterLink,
    DatePipe,
    LabelPipe,
  ],
  templateUrl: './result-detail-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'result-detail-page' },
})
export class ResultDetailPage {
  private readonly labResultService = inject(LabResultService);
  private readonly imagingResultService = inject(ImagingResultService);
  private readonly reportDownloadService = inject(ReportDownloadService);
  private readonly patientContext = inject(PatientContextService);
  private readonly messageService = inject(MessageService);

  readonly patientId = input.required<string>();
  readonly resultId = input.required<string>();
  readonly kind = input<'lab' | 'imaging'>('lab');

  private readonly request = computed(() => ({ kind: this.kind(), id: this.resultId() }));

  protected readonly loaded = toSignal<Loaded>(
    toObservable(this.request).pipe(
      switchMap(({ kind, id }) => {
        if (kind === 'lab') {
          return this.labResultService.getResultById(id).pipe(
            switchMap((result) => of({ kind: 'lab' as const, result })),
            catchError(() => {
              this.messageService.add({ severity: 'error', summary: 'Nie znaleziono wyniku' });
              return of({ kind: 'not-found' as const });
            }),
          );
        }
        return this.imagingResultService.getResultById(id).pipe(
          switchMap((result) => of({ kind: 'imaging' as const, result })),
          catchError(() => {
            this.messageService.add({ severity: 'error', summary: 'Nie znaleziono wyniku' });
            return of({ kind: 'not-found' as const });
          }),
        );
      }),
    ),
    { initialValue: undefined },
  );

  protected readonly pageTitle = computed(() =>
    this.kind() === 'lab' ? 'Wynik badania' : 'Opis badania',
  );

  protected downloadLab(result: LabResult): void {
    const patient = this.patientContext.patient();
    if (!patient) return;
    this.reportDownloadService.downloadLabReport(result, patient);
    this.messageService.add({ severity: 'success', summary: 'Raport pobrany' });
  }

  protected downloadImaging(result: ImagingResult): void {
    const patient = this.patientContext.patient();
    if (!patient) return;
    this.reportDownloadService.downloadImagingReport(
      {
        examName: result.examName,
        findings: result.findings,
        conclusion: result.conclusion,
        performedAt: result.performedAt,
        reportedAt: result.reportedAt,
      },
      patient,
    );
    this.messageService.add({ severity: 'success', summary: 'Raport pobrany' });
  }
}
