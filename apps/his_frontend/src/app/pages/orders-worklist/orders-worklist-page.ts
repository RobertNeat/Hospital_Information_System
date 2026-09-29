import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { forkJoin, map } from 'rxjs';
import { rxResource } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Select } from 'primeng/select';
import { FormsModule } from '@angular/forms';
import { DataTable } from '../../components/data-table/data-table';
import { PageHeader } from '../../components/page-header/page-header';
import { StatusTag } from '../../components/status-tag/status-tag';
import {
  ORDER_STATUS_OPTIONS,
  IMAGING_MODALITY_LABELS,
  URGENCY_OPTIONS,
} from '../../constants/labels';
import type { ID, OrderStatus, OrderUrgency, PatientSummary, TableColumn } from '../../models';
import { LabOrderService } from '../../services/lab-order.service';
import { ImagingOrderService } from '../../services/imaging-order.service';
import { PatientService } from '../../services/patient.service';

type WorklistOrderType = 'lab' | 'imaging';

interface WorklistRow extends Record<string, unknown> {
  id: ID;
  type: WorklistOrderType;
  patientId: ID;
  patientName: string;
  description: string;
  orderedAt: string;
  urgency: OrderUrgency;
  status: OrderStatus;
}

const TYPE_OPTIONS = [
  { label: 'Wszystkie', value: '' },
  { label: 'Laboratoryjne', value: 'lab' },
  { label: 'Obrazowe', value: 'imaging' },
];

const STATUS_OPTIONS = [{ label: 'Wszystkie', value: '' }, ...ORDER_STATUS_OPTIONS];
const URGENCY_FILTER_OPTIONS = [{ label: 'Wszystkie', value: '' }, ...URGENCY_OPTIONS];

/** Valid forward status transitions for the demo "Zmień status" action. */
const NEXT_STATUSES: Record<OrderStatus, OrderStatus[]> = {
  ordered: ['scheduled', 'specimen_collected', 'in_progress', 'cancelled'],
  scheduled: ['specimen_collected', 'in_progress', 'cancelled'],
  specimen_collected: ['in_progress', 'cancelled'],
  in_progress: ['completed', 'cancelled'],
  completed: [],
  cancelled: [],
};

@Component({
  selector: 'app-orders-worklist-page',
  imports: [PageHeader, DataTable, StatusTag, Select, FormsModule, DatePipe],
  templateUrl: './orders-worklist-page.html',
  styleUrl: './orders-worklist-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdersWorklistPage {
  private readonly labOrderService = inject(LabOrderService);
  private readonly imagingOrderService = inject(ImagingOrderService);
  private readonly patientService = inject(PatientService);
  private readonly toast = inject(MessageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly type = input<string>();
  readonly status = input<string>();

  protected readonly typeOptions = TYPE_OPTIONS;
  protected readonly statusOptions = STATUS_OPTIONS;
  protected readonly urgencyOptions = URGENCY_FILTER_OPTIONS;

  protected readonly urgencyFilter = signal<OrderUrgency | ''>('');

  private readonly ordersResource = rxResource({
    stream: () =>
      forkJoin({
        lab: this.labOrderService.getOrders(),
        imaging: this.imagingOrderService.getOrders(),
        patients: this.patientService.getPatients(),
      }).pipe(
        map(({ lab, imaging, patients }) => {
          const patientMap = new Map<ID, PatientSummary>(patients.map((p) => [p.id, p]));
          const nameOf = (pid: ID) => {
            const p = patientMap.get(pid);
            return p ? `${p.lastName} ${p.firstName}` : pid;
          };
          const labRows: WorklistRow[] = lab.map((o) => ({
            id: o.id,
            type: 'lab',
            patientId: o.patientId,
            patientName: nameOf(o.patientId),
            description: `${o.items.length} badań`,
            orderedAt: o.orderedAt,
            urgency: o.urgency,
            status: o.status,
          }));
          const imagingRows: WorklistRow[] = imaging.map((o) => ({
            id: o.id,
            type: 'imaging',
            patientId: o.patientId,
            patientName: nameOf(o.patientId),
            description: `${o.examName} (${IMAGING_MODALITY_LABELS[o.modality]})`,
            orderedAt: o.orderedAt,
            urgency: o.urgency,
            status: o.status,
          }));
          return [...labRows, ...imagingRows].sort((a, b) =>
            b.orderedAt.localeCompare(a.orderedAt),
          );
        }),
      ),
  });

  protected readonly loading = computed(() => this.ordersResource.isLoading());

  protected readonly filteredRows = computed<WorklistRow[]>(() => {
    const all = this.ordersResource.value() ?? [];
    const typeFilter = this.type();
    const statusFilter = this.status();
    const urgency = this.urgencyFilter();
    return all.filter((row) => {
      if (typeFilter && row.type !== typeFilter) return false;
      if (statusFilter && row.status !== statusFilter) return false;
      if (urgency && row.urgency !== urgency) return false;
      return true;
    });
  });

  protected readonly statCounts = computed(() => {
    const all = this.ordersResource.value() ?? [];
    const counts: Record<OrderStatus, number> = {
      ordered: 0,
      scheduled: 0,
      specimen_collected: 0,
      in_progress: 0,
      completed: 0,
      cancelled: 0,
    };
    for (const row of all) counts[row.status]++;
    return counts;
  });

  protected readonly columns: TableColumn<WorklistRow>[] = [
    { field: 'patientName', header: 'Pacjent', sortable: true },
    { field: 'type', header: 'Typ', type: 'custom' },
    { field: 'description', header: 'Opis' },
    { field: 'orderedAt', header: 'Data zlecenia', type: 'datetime', sortable: true },
    { field: 'urgency', header: 'Pilność', type: 'tag', tagKind: 'urgency' },
    { field: 'status', header: 'Status', type: 'tag', tagKind: 'orderStatus' },
  ];

  protected nextStatusOptions(row: WorklistRow) {
    return NEXT_STATUSES[row.status].map((s) => ({
      label: ORDER_STATUS_OPTIONS.find((o) => o.value === s)?.label ?? s,
      value: s,
    }));
  }

  protected onTypeChange(value: string): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { type: value || null },
      queryParamsHandling: 'merge',
    });
  }

  protected onStatusChange(value: string): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { status: value || null },
      queryParamsHandling: 'merge',
    });
  }

  protected changeStatus(row: WorklistRow, newStatus: OrderStatus | null): void {
    if (!newStatus) return;
    const onUpdated = () => {
      this.toast.add({
        severity: 'success',
        summary: 'Status zlecenia zaktualizowany',
      });
      this.ordersResource.reload();
    };
    if (row.type === 'lab') {
      this.labOrderService.updateStatus(row.id, newStatus).subscribe(onUpdated);
    } else {
      this.imagingOrderService.updateStatus(row.id, newStatus).subscribe(onUpdated);
    }
  }
}
