import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DatePicker } from 'primeng/datepicker';
import { Select } from 'primeng/select';
import { DataTable } from '../../components/data-table/data-table';
import { EmptyState } from '../../components/empty-state/empty-state';
import { PageHeader } from '../../components/page-header/page-header';
import { PRESCRIPTION_STATUS_OPTIONS } from '../../constants/labels';
import type { Prescription, TableColumn } from '../../models';
import { PrescriptionService } from '../../services/prescription.service';
import { StaffService } from '../../services/staff.service';

// `type` (not `interface`) so it structurally satisfies `DataTable`'s
// `T extends Record<string, unknown>` constraint (interfaces don't).
type PrescriptionRow = {
  id: string;
  patientId: string;
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
})
export class PrescriptionsListPage {
  private readonly prescriptionService = inject(PrescriptionService);
  private readonly staffService = inject(StaffService);
  private readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly prescriptions = signal<Prescription[]>([]);

  protected readonly statusFilter = signal<string | null>(null);
  protected readonly dateFrom = signal<Date | null>(null);
  protected readonly dateTo = signal<Date | null>(null);
  protected readonly statusOptions = PRESCRIPTION_STATUS_OPTIONS;

  protected readonly columns: TableColumn<PrescriptionRow>[] = [
    { field: 'id', header: 'Nr recepty', sortable: true },
    { field: 'patientId', header: 'Pacjent' },
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
    this.prescriptionService
      .getPrescriptions({ prescriberId: this.staffService.currentUser().id })
      .subscribe((rows) => {
        this.prescriptions.set(rows);
        this.loading.set(false);
      });
  }

  protected readonly filteredRows = computed((): PrescriptionRow[] => {
    const status = this.statusFilter();
    const from = this.dateFrom();
    const to = this.dateTo();

    return this.prescriptions()
      .filter((p) => !status || p.status === status)
      .filter((p) => !from || p.issuedAt.slice(0, 10) >= toIsoDate(from))
      .filter((p) => !to || p.issuedAt.slice(0, 10) <= toIsoDate(to))
      .map((p) => ({
        id: p.id,
        patientId: p.patientId,
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

function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
