import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin, catchError, of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { DatePicker } from 'primeng/datepicker';
import { Select } from 'primeng/select';
import { DataTable } from '../../components/data-table/data-table';
import { EmptyState } from '../../components/empty-state/empty-state';
import { PageHeader } from '../../components/page-header/page-header';
import { PRESCRIPTION_STATUS_OPTIONS } from '../../constants/labels';
import type { ID, Prescription, PatientSummary, TableColumn } from '../../models';
import { PatientService } from '../../services/patient.service';
import { PrescriptionService } from '../../services/prescription.service';
import { StaffService } from '../../services/staff.service';

type PrescriptionRow = {
  id: string;
  patientId: string;
  patientName: string;
  issuedAt: string;
  validUntil: string;
  status: string;
  itemCount: number;
};

@Component({
  selector: 'app-prescriptions-list-page',
  imports: [PageHeader, EmptyState, DataTable, Select, DatePicker, FormsModule],
  templateUrl: './prescriptions-list-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'prescriptions-list-page' },
})
export class PrescriptionsListPage {
  private readonly prescriptionService = inject(PrescriptionService);
  private readonly patientService = inject(PatientService);
  private readonly staffService = inject(StaffService);
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly prescriptions = signal<Prescription[]>([]);
  private readonly patientsById = signal<Map<ID, PatientSummary>>(new Map());

  protected readonly statusFilter = signal<string | null>(null);
  protected readonly dateFrom = signal<Date | null>(null);
  protected readonly dateTo = signal<Date | null>(null);
  protected readonly statusOptions = PRESCRIPTION_STATUS_OPTIONS;

  protected readonly columns: TableColumn<PrescriptionRow>[] = [
    { field: 'id', header: 'Nr recepty', sortable: true },
    { field: 'patientName', header: 'Pacjent', sortable: true },
    { field: 'issuedAt', header: 'Data wystawienia', type: 'datetime', sortable: true },
    { field: 'validUntil', header: 'Ważna do', type: 'date', sortable: true },
    {
      field: 'status',
      header: 'Status',
      type: 'tag',
      tagKind: 'prescriptionStatus',
      sortable: true,
    },
  ];

  constructor() {
    forkJoin({
      prescriptions: this.prescriptionService
        .getPrescriptions({
          prescriberId: this.staffService.currentUser().id,
        })
        .pipe(
          catchError(() => {
            this.messageService.add({
              severity: 'error',
              summary: 'Recepty',
              detail: 'Nie udało się wczytać listy recept.',
            });
            return of<Prescription[]>([]);
          }),
        ),
      // The prescription DTO carries only patientId; patients are fetched separately to
      // resolve display names. A failed fetch must not block the prescriptions list.
      patients: this.patientService.getPatients().pipe(catchError(() => of([]))),
    }).subscribe(({ prescriptions, patients }) => {
      this.patientsById.set(new Map(patients.map((p) => [p.id, p])));
      this.prescriptions.set(prescriptions);
      this.loading.set(false);
    });
  }

  protected readonly filteredRows = computed((): PrescriptionRow[] => {
    const status = this.statusFilter();
    const from = this.dateFrom();
    const to = this.dateTo();
    const patientsById = this.patientsById();

    return this.prescriptions()
      .filter((p) => !status || p.status === status)
      .filter((p) => !from || p.issuedAt.slice(0, 10) >= toIsoDate(from))
      .filter((p) => !to || p.issuedAt.slice(0, 10) <= toIsoDate(to))
      .map((p) => ({
        id: p.id,
        patientId: p.patientId,
        patientName: nameOf(p.patientId, patientsById),
        issuedAt: p.issuedAt,
        validUntil: p.validUntil,
        status: p.status,
        itemCount: p.items.length,
      }));
  });

  protected openPrescription(row: PrescriptionRow): void {
    this.router.navigate(['/patients', row.patientId, 'prescriptions']);
  }
}

/** Falls back to the raw UUID when the patient cannot be resolved (e.g. fetch failed). */
function nameOf(patientId: ID, patientsById: Map<ID, PatientSummary>): string {
  const patient = patientsById.get(patientId);
  return patient ? `${patient.lastName} ${patient.firstName}` : patientId;
}

function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
