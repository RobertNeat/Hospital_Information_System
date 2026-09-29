import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { switchMap } from 'rxjs';
import { Select } from 'primeng/select';
import { Tag } from 'primeng/tag';
import { DataTable } from '../../components/data-table/data-table';
import { PageHeader } from '../../components/page-header/page-header';
import { StatCard } from '../../components/stat-card/stat-card';
import { AgePipe } from '../../pipes/age.pipe';
import { FullNamePipe } from '../../pipes/full-name.pipe';
import type { TableColumn, VitalType, WardVitalsRow } from '../../models';
import { VitalsService } from '../../services/vitals.service';
import { WardService } from '../../services/ward.service';

interface BoardRow extends Record<string, unknown> {
  id: string;
  patientId: string;
  fullNameSource: WardVitalsRow['patient'];
  systolic: number | null;
  diastolic: number | null;
  heartRate: number | null;
  temperature: number | null;
  spo2: number | null;
  respiratoryRate: number | null;
  lastMeasuredAgoMin: number | null;
  criticalCount: number;
  warningCount: number;
  anomalyLevels: Partial<Record<VitalType, 'warning' | 'critical'>>;
}

function anomalyLevelsOf(r: WardVitalsRow): Partial<Record<VitalType, 'warning' | 'critical'>> {
  const map: Partial<Record<VitalType, 'warning' | 'critical'>> = {};
  for (const a of r.anomalies) map[a.type] = a.severity;
  return map;
}

const NO_MEASUREMENT_THRESHOLD_MIN = 8 * 60;

@Component({
  selector: 'app-vitals-board-page',
  imports: [FormsModule, PageHeader, StatCard, Select, Tag, DataTable, FullNamePipe, AgePipe],
  templateUrl: './vitals-board-page.html',
  styleUrl: './vitals-board-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'vitals-board-page' },
})
export class VitalsBoardPage {
  private readonly vitalsService = inject(VitalsService);
  private readonly wardService = inject(WardService);
  private readonly router = inject(Router);

  readonly ward = input<string | undefined>(undefined);

  protected readonly wards = toSignal(this.wardService.getWards(), { initialValue: [] });
  protected readonly wardOptions = computed(() => [
    { label: 'Wszystkie oddziały', value: undefined },
    ...this.wards().map((w) => ({ label: w.name, value: w.id })),
  ]);

  private readonly overview = toSignal(
    toObservable(this.ward).pipe(switchMap((wardId) => this.vitalsService.getWardOverview(wardId))),
    { initialValue: [] as WardVitalsRow[] },
  );

  protected readonly rows = computed<BoardRow[]>(() =>
    this.overview().map((r) => ({
      id: r.patient.id,
      patientId: r.patient.id,
      fullNameSource: r.patient,
      systolic: r.latest?.systolic ?? null,
      diastolic: r.latest?.diastolic ?? null,
      heartRate: r.latest?.heartRate ?? null,
      temperature: r.latest?.temperature ?? null,
      spo2: r.latest?.spo2 ?? null,
      respiratoryRate: r.latest?.respiratoryRate ?? null,
      lastMeasuredAgoMin: r.lastMeasuredAgoMin ?? null,
      criticalCount: r.anomalies.filter((a) => a.severity === 'critical').length,
      warningCount: r.anomalies.filter((a) => a.severity === 'warning').length,
      anomalyLevels: anomalyLevelsOf(r),
    })),
  );

  protected readonly columns: TableColumn<BoardRow>[] = [
    { field: 'fullNameSource', header: 'Pacjent' },
    { field: 'systolic', header: 'RR sk.' },
    { field: 'diastolic', header: 'RR rozk.' },
    { field: 'heartRate', header: 'Tętno' },
    { field: 'temperature', header: 'Temp.' },
    { field: 'spo2', header: 'SpO₂' },
    { field: 'respiratoryRate', header: 'Oddechy' },
    { field: 'lastMeasuredAgoMin', header: 'Ostatni pomiar' },
    { field: 'criticalCount', header: 'Odchylenia' },
  ];

  protected readonly patientsMonitored = computed(() => this.overview().length);
  protected readonly criticalCount = computed(
    () => this.overview().filter((r) => r.anomalies.some((a) => a.severity === 'critical')).length,
  );
  protected readonly warningCount = computed(
    () =>
      this.overview().filter(
        (r) => r.anomalies.length > 0 && !r.anomalies.some((a) => a.severity === 'critical'),
      ).length,
  );
  protected readonly noRecentMeasurementCount = computed(
    () =>
      this.overview().filter(
        (r) => !r.latest || (r.lastMeasuredAgoMin ?? 0) > NO_MEASUREMENT_THRESHOLD_MIN,
      ).length,
  );

  protected onWardChange(wardId: string | undefined): void {
    this.router.navigate([], {
      queryParams: { ward: wardId ?? null },
      queryParamsHandling: 'merge',
    });
  }

  protected onRowSelect(row: BoardRow): void {
    this.router.navigate(['/patients', row.patientId, 'vitals']);
  }

  /** Formats minutes-ago as e.g. "5 min", "3 h 20 min" or "2 d 4 h" for readability. */
  protected formatAgo(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    const hours = Math.floor(minutes / 60);
    const remMinutes = minutes % 60;
    if (hours < 24) return remMinutes > 0 ? `${hours} h ${remMinutes} min` : `${hours} h`;
    const days = Math.floor(hours / 24);
    const remHours = hours % 24;
    return remHours > 0 ? `${days} d ${remHours} h` : `${days} d`;
  }
}
