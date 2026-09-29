import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { switchMap } from 'rxjs';
import { MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { Tooltip } from 'primeng/tooltip';
import { PageHeader } from '../../components/page-header/page-header';
import { EmptyState } from '../../components/empty-state/empty-state';
import { StatusTag } from '../../components/status-tag/status-tag';
import { LabResultObservations } from '../../components/lab-result-observations/lab-result-observations';
import { ResultCompareDialog } from '../../components/result-compare-dialog/result-compare-dialog';
import { LabelPipe } from '../../pipes/label.pipe';
import { LabResultService } from '../../services/lab-result.service';
import { ImagingResultService } from '../../services/imaging-result.service';
import { ReportDownloadService } from '../../services/report-download.service';
import { PatientContextService } from '../../services/patient-context.service';
import type { ImagingResult, LabResult, SelectOption } from '../../models';

@Component({
  selector: 'app-patient-results-page',
  imports: [
    PageHeader,
    EmptyState,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    TableModule,
    StatusTag,
    Tag,
    LabResultObservations,
    ResultCompareDialog,
    Button,
    Tooltip,
    DatePipe,
    LabelPipe,
  ],
  templateUrl: './patient-results-page.html',
  styleUrl: './patient-results-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatientResultsPage {
  private readonly labResultService = inject(LabResultService);
  private readonly imagingResultService = inject(ImagingResultService);
  private readonly reportDownloadService = inject(ReportDownloadService);
  private readonly patientContext = inject(PatientContextService);
  private readonly messageService = inject(MessageService);
  private readonly router = inject(Router);

  readonly patientId = input.required<string>();
  readonly tab = input<string>();

  protected readonly activeTab = computed(() => this.tab() ?? 'lab');

  protected readonly lab = toSignal(
    toObservable(this.patientId).pipe(switchMap((pid) => this.labResultService.getResults(pid))),
    { initialValue: [] as LabResult[] },
  );

  protected readonly imaging = toSignal(
    toObservable(this.patientId).pipe(
      switchMap((pid) => this.imagingResultService.getResults(pid)),
    ),
    { initialValue: [] as ImagingResult[] },
  );

  protected readonly abnormalCount = (result: LabResult): number =>
    result.observations.filter((o) => o.flag !== 'N').length;

  protected readonly expandedLabRows = signal<Record<string, boolean>>({});

  protected onLabRowExpand(result: LabResult): void {
    this.expandedLabRows.update((rows) => ({ ...rows, [result.id]: true }));
  }

  protected onLabRowCollapse(result: LabResult): void {
    this.expandedLabRows.update((rows) => {
      const rest = { ...rows };
      delete rest[result.id];
      return rest;
    });
  }

  protected readonly trendableAnalytes = signal<SelectOption[]>([]);

  protected readonly compareVisible = signal(false);
  protected readonly compareAnalyteCode = signal<string | undefined>(undefined);

  protected onTabChange(value: string | number | undefined): void {
    this.router.navigate([], {
      queryParams: { tab: value === undefined ? 'lab' : String(value) },
      queryParamsHandling: 'merge',
    });
  }

  protected navigateLab(result: LabResult): void {
    this.router.navigate(['/patients', this.patientId(), 'results', 'lab', result.id]);
  }

  protected navigateImaging(result: ImagingResult): void {
    this.router.navigate(['/patients', this.patientId(), 'results', 'imaging', result.id]);
  }

  protected downloadLabReport(result: LabResult): void {
    const patient = this.patientContext.patient();
    if (!patient) return;
    this.reportDownloadService.downloadLabReport(result, patient);
    this.messageService.add({ severity: 'success', summary: 'Raport pobrany' });
  }

  protected downloadImagingReport(result: ImagingResult): void {
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

  protected openCompare(analyteCode?: string): void {
    this.labResultService.getTrendableAnalytes(this.patientId()).subscribe((options) => {
      this.trendableAnalytes.set(options);
      this.compareAnalyteCode.set(analyteCode ?? options[0]?.value);
      this.compareVisible.set(true);
    });
  }
}
