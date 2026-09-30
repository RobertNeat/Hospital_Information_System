import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { PageHeader } from '../../components/page-header/page-header';
import { DataTable } from '../../components/data-table/data-table';
import { StatusTag } from '../../components/status-tag/status-tag';
import { PatientService } from '../../services/patient.service';
import { WardService } from '../../services/ward.service';
import { AgePipe } from '../../pipes/age.pipe';
import { FullNamePipe } from '../../pipes/full-name.pipe';
import { ADMISSION_STATUS_OPTIONS, PATIENT_FLAG_LABELS } from '../../constants/labels';
import type { AdmissionStatus, PatientSummary, TableColumn, Ward } from '../../models';

const STATUS_FILTER_OPTIONS: { label: string; value: AdmissionStatus | '' }[] = [
  { label: 'Wszyscy', value: '' },
  ...ADMISSION_STATUS_OPTIONS,
];

@Component({
  selector: 'app-patient-list-page',
  imports: [
    InputText,
    PageHeader,
    DataTable,
    StatusTag,
    Button,
    Select,
    Tooltip,
    FormsModule,
    AgePipe,
    FullNamePipe,
  ],
  templateUrl: './patient-list-page.html',
  styleUrl: './patient-list-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'patient-list-page' },
})
export class PatientListPage {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly patientService = inject(PatientService);
  private readonly wardService = inject(WardService);

  readonly q = input('');
  readonly status = input<AdmissionStatus | ''>('');
  readonly ward = input('');

  protected readonly statusOptions = STATUS_FILTER_OPTIONS;
  protected readonly wardOptions = signal<{ label: string; value: string }[]>([
    { label: 'Wszystkie oddziały', value: '' },
  ]);

  protected readonly searchTerm = signal('');
  protected readonly selectedStatus = signal<AdmissionStatus | ''>('');
  protected readonly selectedWard = signal('');

  protected readonly rows = signal<PatientSummary[]>([]);
  protected readonly loading = signal(true);

  protected readonly columns: TableColumn<PatientSummary>[] = [
    { field: 'lastName', header: 'Nazwisko i imię', sortable: true },
    { field: 'pesel', header: 'PESEL' },
    { field: 'birthDate', header: 'Wiek' },
    { field: 'gender', header: 'Płeć' },
    { field: 'mrn', header: 'Nr historii choroby' },
    { field: 'wardName', header: 'Oddział/Sala' },
    { field: 'status', header: 'Status', type: 'tag', tagKind: 'admissionStatus' },
    { field: 'flags', header: 'Flagi' },
  ];

  private readonly filterState = computed(() => ({
    term: this.searchTerm(),
    status: this.selectedStatus(),
    wardId: this.selectedWard(),
  }));

  constructor() {
    // Follow the route-bound inputs reactively so back/forward navigation re-syncs the filters.
    effect(() => this.searchTerm.set(this.q() ?? ''));
    effect(() => this.selectedStatus.set(this.status() ?? ''));
    effect(() => this.selectedWard.set(this.ward() ?? ''));

    this.wardService.getWards().subscribe((wards: Ward[]) => {
      this.wardOptions.set([
        { label: 'Wszystkie oddziały', value: '' },
        ...wards.map((w) => ({ label: w.name, value: w.id })),
      ]);
    });

    toObservable(this.filterState)
      .pipe(
        debounceTime(300),
        distinctUntilChanged(
          (a, b) => a.term === b.term && a.status === b.status && a.wardId === b.wardId,
        ),
        switchMap(({ term, status, wardId }) => {
          this.loading.set(true);
          return this.patientService.getPatients({
            term: term || undefined,
            status: status || undefined,
            wardId: wardId || undefined,
          });
        }),
        takeUntilDestroyed(),
      )
      .subscribe((patients) => {
        this.rows.set(patients);
        this.loading.set(false);
      });
  }

  protected onSearchInput(value: string): void {
    this.searchTerm.set(value);
    this.updateQueryParams({ q: value || null });
  }

  protected onStatusChange(value: AdmissionStatus | ''): void {
    this.selectedStatus.set(value);
    this.updateQueryParams({ status: value || null });
  }

  protected onWardChange(value: string): void {
    this.selectedWard.set(value);
    this.updateQueryParams({ ward: value || null });
  }

  private updateQueryParams(params: Record<string, string | null>): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected flagLabels(row: PatientSummary): string {
    return row.flags.map((f) => PATIENT_FLAG_LABELS[f]).join(', ');
  }

  protected openChart(row: PatientSummary): void {
    this.router.navigate(['/patients', row.id]);
  }

  protected editPatient(row: PatientSummary, event: Event): void {
    event.stopPropagation();
    this.router.navigate(['/patients', row.id, 'edit']);
  }

  protected newLabOrder(row: PatientSummary, event: Event): void {
    event.stopPropagation();
    this.router.navigate(['/patients', row.id, 'orders', 'lab', 'new']);
  }

  protected newPrescription(row: PatientSummary, event: Event): void {
    event.stopPropagation();
    this.router.navigate(['/patients', row.id, 'prescriptions', 'new']);
  }

  protected goToRegister(): void {
    this.router.navigate(['/patients', 'register']);
  }
}
