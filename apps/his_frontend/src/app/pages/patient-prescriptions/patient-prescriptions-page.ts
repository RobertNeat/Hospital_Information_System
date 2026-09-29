import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { TableModule, type TableRowCollapseEvent, type TableRowExpandEvent } from 'primeng/table';
import { Tooltip } from 'primeng/tooltip';
import { EmptyState } from '../../components/empty-state/empty-state';
import { PageHeader } from '../../components/page-header/page-header';
import { SectionHeader } from '../../components/section-header/section-header';
import { StatusTag } from '../../components/status-tag/status-tag';
import { LabelPipe } from '../../pipes/label.pipe';
import type { Prescription, PrescriptionItem } from '../../models';
import { PrescriptionService } from '../../services/prescription.service';

interface ActiveMedicationRow {
  prescriptionId: string;
  drugId: string;
  drugName: string;
  strength: string;
  dose: string;
  frequency: string;
  route: string;
  timesOfDay: string;
  endDate: string;
}

@Component({
  selector: 'app-patient-prescriptions-page',
  imports: [
    PageHeader,
    SectionHeader,
    EmptyState,
    TableModule,
    StatusTag,
    Button,
    Tooltip,
    DatePipe,
    LabelPipe,
  ],
  templateUrl: './patient-prescriptions-page.html',
  styleUrl: './patient-prescriptions-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatientPrescriptionsPage {
  readonly patientId = input.required<string>();

  private readonly prescriptionService = inject(PrescriptionService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);
  private readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly prescriptions = signal<Prescription[]>([]);

  constructor() {
    // `patientId` (input.required) is not yet available synchronously in the constructor --
    // reading it here throws NG0950. `effect()` defers the first run until after inputs are
    // bound, and re-loads if `patientId` ever changes (e.g. navigating between patients).
    effect(() => {
      this.load(this.patientId());
    });
  }

  private load(patientId: string): void {
    this.loading.set(true);
    this.prescriptionService.getPrescriptions({ patientId }).subscribe((rows) => {
      this.prescriptions.set(rows);
      this.loading.set(false);
    });
  }

  /**
   * `PrescriptionService.getActiveMedications` returns flattened `PrescriptionItem[]` with no
   * prescription id or date, so the "end date" column can't be derived from it (service gap,
   * see report). Instead we derive active medications from the already-loaded prescriptions
   * list, applying the same filter the service itself uses (issued/partially_dispensed and
   * not yet past validUntil), and compute the end date as validFrom + item.durationDays.
   */
  protected readonly activeMedications = computed((): ActiveMedicationRow[] => {
    const today = new Date().toISOString().slice(0, 10);
    const rows: ActiveMedicationRow[] = [];
    for (const p of this.prescriptions()) {
      if (p.status !== 'issued' && p.status !== 'partially_dispensed') continue;
      if (p.validUntil < today) continue;
      for (const item of p.items) {
        rows.push({
          prescriptionId: p.id,
          drugId: item.drugId,
          drugName: item.drugName,
          strength: item.strength,
          dose: `${item.dosage.dose} ${item.dosage.doseUnit}`,
          frequency: item.dosage.frequency,
          route: item.dosage.route,
          timesOfDay: (item.dosage.timesOfDay ?? []).join(', ') || '—',
          endDate: addDays(p.validFrom, item.dosage.durationDays),
        });
      }
    }
    return rows;
  });

  protected readonly expandedRows = signal<Record<string, boolean>>({});

  protected onRowExpand(event: TableRowExpandEvent<Prescription>): void {
    this.expandedRows.update((rows) => ({ ...rows, [event.data.id]: true }));
  }

  protected onRowCollapse(event: TableRowCollapseEvent): void {
    this.expandedRows.update((rows) => {
      const rest = { ...rows };
      delete rest[event.data.id];
      return rest;
    });
  }

  protected itemsOf(prescription: Prescription): PrescriptionItem[] {
    return prescription.items;
  }

  protected canCancel(p: Prescription): boolean {
    return p.status === 'issued' || p.status === 'partially_dispensed';
  }

  protected cancel(p: Prescription): void {
    this.confirmationService.confirm({
      header: 'Anulowanie recepty',
      message: `Czy na pewno anulować receptę ${p.id}?`,
      acceptLabel: 'Tak, anuluj',
      rejectLabel: 'Nie',
      accept: () => {
        this.prescriptionService.cancel(p.id).subscribe({
          next: () => {
            this.messageService.add({ severity: 'success', summary: 'Recepta anulowana.' });
            this.load(this.patientId());
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: 'Nie udało się anulować recepty.',
            });
          },
        });
      },
    });
  }

  protected goToNewPrescription(): void {
    this.router.navigate(['/patients', this.patientId(), 'prescriptions', 'new']);
  }
}

function addDays(isoDate: string, days: number): string {
  const d = new Date(isoDate);
  d.setDate(d.getDate() + days);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
