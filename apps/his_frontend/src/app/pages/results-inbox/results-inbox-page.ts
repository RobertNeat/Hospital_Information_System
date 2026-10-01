import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { SelectButton } from 'primeng/selectbutton';
import { Tooltip } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { PageHeader } from '../../components/page-header/page-header';
import { EmptyState } from '../../components/empty-state/empty-state';
import { DataTable } from '../../components/data-table/data-table';
import { SectionHeader } from '../../components/section-header/section-header';
import { LabResultService } from '../../services/lab-result.service';
import { ImagingResultService } from '../../services/imaging-result.service';
import { LabelPipe } from '../../pipes/label.pipe';
import type {
  ImagingResult,
  LabResult,
  PatientSummary,
  ResultAbnormalityFilter,
  ResultWithPatient,
  TableColumn,
} from '../../models';

/** Inbox rows may lack `patient`; the name then falls back to the patient id. */
const patientName = (r: { patientId: string; patient?: PatientSummary }): string =>
  r.patient ? `${r.patient.lastName} ${r.patient.firstName}` : r.patientId;

type LabResultRow = ResultWithPatient<LabResult> & { patientName: string };

type ImagingResultRow = ResultWithPatient<ImagingResult> & { patientName: string };

const FILTER_OPTIONS = [
  { label: 'Wszystkie', value: 'all' as const },
  { label: 'Nieprawidłowe', value: 'abnormal' as const },
  { label: 'Krytyczne', value: 'critical' as const },
];

@Component({
  selector: 'app-results-inbox-page',
  imports: [
    PageHeader,
    EmptyState,
    SelectButton,
    FormsModule,
    DataTable,
    SectionHeader,
    RouterLink,
    Tooltip,
    LabelPipe,
  ],
  templateUrl: './results-inbox-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'results-inbox-page' },
})
export class ResultsInboxPage {
  private readonly labResultService = inject(LabResultService);
  private readonly imagingResultService = inject(ImagingResultService);
  private readonly router = inject(Router);

  readonly filter = input<ResultAbnormalityFilter | undefined>('all');

  protected readonly filterOptions = FILTER_OPTIONS;

  protected readonly effectiveFilter = computed(() => this.filter() ?? 'all');

  private readonly results = toSignal(
    toObservable(this.effectiveFilter).pipe(switchMap((f) => this.labResultService.getRecent(f))),
    { initialValue: [] as ResultWithPatient<LabResult>[] },
  );

  protected readonly columns: TableColumn<LabResultRow>[] = [
    { field: 'patientName', header: 'Pacjent', sortable: true },
    { field: 'collectedAt', header: 'Data pobrania', type: 'datetime', sortable: true },
    { field: 'testName', header: 'Badanie', sortable: true },
    { field: 'category', header: 'Kategoria' },
    { field: 'status', header: 'Status', type: 'tag', tagKind: 'resultStatus' },
  ];

  protected readonly rows = computed<LabResultRow[]>(() =>
    this.results().map((r) => ({
      ...r,
      patientName: patientName(r),
    })),
  );

  private readonly imagingResults = toSignal(
    toObservable(this.effectiveFilter).pipe(
      switchMap((f) => this.imagingResultService.getRecent(f)),
    ),
    { initialValue: [] as ResultWithPatient<ImagingResult>[] },
  );

  protected readonly imagingColumns: TableColumn<ImagingResultRow>[] = [
    { field: 'patientName', header: 'Pacjent', sortable: true },
    { field: 'performedAt', header: 'Data', type: 'datetime', sortable: true },
    { field: 'modality', header: 'Modalność' },
    { field: 'examName', header: 'Badanie', sortable: true },
    { field: 'bodyRegion', header: 'Okolica' },
  ];

  protected readonly imagingRows = computed<ImagingResultRow[]>(() =>
    this.imagingResults().map((r) => ({
      ...r,
      patientName: patientName(r),
    })),
  );

  protected onFilterChange(value: ResultAbnormalityFilter): void {
    this.router.navigate([], { queryParams: { filter: value }, queryParamsHandling: 'merge' });
  }

  protected openPatientResults(row: LabResultRow): void {
    this.router.navigate(['/patients', row.patientId, 'results'], {
      queryParams: { tab: 'lab' },
    });
  }

  protected openPatientImagingResults(row: ImagingResultRow): void {
    this.router.navigate(['/patients', row.patientId, 'results'], {
      queryParams: { tab: 'imaging' },
    });
  }
}
