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
import { forkJoin } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { TableModule, type TableRowCollapseEvent, type TableRowExpandEvent } from 'primeng/table';
import { Tooltip } from 'primeng/tooltip';
import { EmptyState } from '../../components/empty-state/empty-state';
import { PageHeader } from '../../components/page-header/page-header';
import { SectionHeader } from '../../components/section-header/section-header';
import { StatusTag } from '../../components/status-tag/status-tag';
import { LabelPipe } from '../../pipes/label.pipe';
import type { ActiveMedication, Prescription, PrescriptionItem } from '../../models';
import { AuthService } from '../../services/auth.service';
import { PrescriptionService } from '../../services/prescription.service';
import { PERMISSIONS } from '../../constants/permissions';
import { toApiError } from '../../utils/api-error';

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
  host: { 'data-component-id': 'patient-prescriptions-page' },
})
export class PatientPrescriptionsPage {
  readonly patientId = input.required<string>();

  private readonly prescriptionService = inject(PrescriptionService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly canCreate = computed(() =>
    this.auth.hasPermission(PERMISSIONS.PRESCRIPTION_CREATE),
  );

  protected readonly loading = signal(true);
  /**
   * Naprawiony blad: brak obslugi bledu w forkJoin zostawial `loading` na `true` na zawsze przy
   * 403/innym bledzie, a sekcja "Schemat leczenia" ignorowala `loading()` wiec od razu pokazywala
   * "Brak aktywnych lekow" - wygladalo jak prawdziwy wynik, a nie jak nieudane zaladowanie danych.
   */
  protected readonly loadError = signal<string | null>(null);
  protected readonly prescriptions = signal<Prescription[]>([]);
  private readonly medications = signal<ActiveMedication[]>([]);

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
    this.loadError.set(null);
    forkJoin({
      rows: this.prescriptionService.getPrescriptions({ patientId }),
      medications: this.prescriptionService.getActiveMedications(patientId),
    }).subscribe({
      next: ({ rows, medications }) => {
        this.prescriptions.set(rows);
        this.medications.set(medications);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loadError.set(
          toApiError(err).problem.detail ?? 'Nie udało się wczytać leków i recept.',
        );
        this.loading.set(false);
      },
    });
  }

  protected readonly activeMedications = computed((): ActiveMedicationRow[] =>
    this.medications().map((m) => ({
      prescriptionId: m.prescriptionId,
      drugId: m.drugId,
      drugName: m.drugName,
      strength: m.strength,
      dose: `${m.dosage.dose} ${m.dosage.doseUnit}`,
      frequency: m.dosage.frequency,
      route: m.dosage.route,
      timesOfDay: (m.dosage.timesOfDay ?? []).join(', ') || '—',
      endDate: addDays(m.date, m.dosage.durationDays),
    })),
  );

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
        this.prescriptionService.cancel(p.id, undefined, p.version).subscribe({
          next: () => {
            this.messageService.add({ severity: 'success', summary: 'Recepta anulowana.' });
            this.load(this.patientId());
          },
          error: (err: unknown) => {
            this.messageService.add({
              severity: 'error',
              summary: 'Nie udało się anulować recepty.',
              detail: toApiError(err).problem.detail,
            });
            // 409: the prescription changed (version/status); show the current state.
            this.load(this.patientId());
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
