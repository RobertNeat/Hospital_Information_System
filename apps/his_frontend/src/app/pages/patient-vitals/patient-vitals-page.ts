import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { switchMap } from 'rxjs';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { SelectButton } from 'primeng/selectbutton';
import { DataTable } from '../../components/data-table/data-table';
import { PageHeader } from '../../components/page-header/page-header';
import { SectionHeader } from '../../components/section-header/section-header';
import {
  TrendChartCard,
  type TrendSeries,
} from '../../components/trend-chart-card/trend-chart-card';
import { VitalsCompareTable } from '../../components/vitals-compare-table/vitals-compare-table';
import {
  VitalsEntryForm,
  type VitalsSaveResult,
} from '../../components/vitals-entry-form/vitals-entry-form';
import { VITAL_THRESHOLDS } from '../../constants/vitals-thresholds';
import type { ResultFlag, TableColumn, VitalAnomaly, VitalSigns, VitalType } from '../../models';
import { VitalsService } from '../../services/vitals.service';
import { evaluateVitals } from '../../utils/vitals-anomaly';

type VitalsRange = '24h' | '7d' | '30d' | 'all';

const VALID_RANGES: VitalsRange[] = ['24h', '7d', '30d', 'all'];
const DEFAULT_RANGE: VitalsRange = '7d';

interface HistoryRow extends Record<string, unknown> {
  id: string;
  recordedAt: string;
  context: string;
  systolic: number | null;
  diastolic: number | null;
  heartRate: number | null;
  temperature: number | null;
  spo2: number | null;
  respiratoryRate: number | null;
  anomalyLevels: Partial<Record<VitalType, 'warning' | 'critical'>>;
}

const RANGE_OPTIONS: { label: string; value: VitalsRange }[] = [
  { label: '24 h', value: '24h' },
  { label: '7 dni', value: '7d' },
  { label: '30 dni', value: '30d' },
  { label: 'Wszystko', value: 'all' },
];

/** Maps an anomaly's severity/direction to a `ResultFlag` so `TrendChartCard` colors the point red. */
function anomalyToFlag(anomaly: VitalAnomaly | undefined): ResultFlag | undefined {
  if (!anomaly) return undefined;
  if (anomaly.severity === 'critical') return anomaly.direction === 'low' ? 'LL' : 'HH';
  return anomaly.direction === 'low' ? 'L' : 'H';
}

function toSeries(label: string, type: VitalType, vitals: VitalSigns[]): TrendSeries {
  return {
    label,
    points: vitals
      .filter((v) => v[type] !== undefined)
      .map((v) => {
        const anomaly = evaluateVitals(v).find((a) => a.type === type);
        return { at: v.recordedAt, value: v[type] as number, flag: anomalyToFlag(anomaly) };
      }),
  };
}

function anomalyLevelsOf(v: VitalSigns): Partial<Record<VitalType, 'warning' | 'critical'>> {
  const map: Partial<Record<VitalType, 'warning' | 'critical'>> = {};
  for (const a of evaluateVitals(v)) map[a.type] = a.severity;
  return map;
}

@Component({
  selector: 'app-patient-vitals-page',
  imports: [
    DatePipe,
    FormsModule,
    PageHeader,
    SectionHeader,
    SelectButton,
    Select,
    Message,
    TrendChartCard,
    VitalsEntryForm,
    VitalsCompareTable,
    DataTable,
  ],
  templateUrl: './patient-vitals-page.html',
  styleUrl: './patient-vitals-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatientVitalsPage {
  private readonly vitalsService = inject(VitalsService);
  private readonly router = inject(Router);

  readonly patientId = input.required<string>();
  // `withComponentInputBinding()` calls setInput(undefined) when the `range` query param is
  // absent (e.g. a plain nav from the chart nav), overriding any input() default -- so this
  // is deliberately untyped/optional here, and `effectiveRange` below is the validated signal
  // every computed/template binding actually reads.
  readonly range = input<string>();

  protected readonly effectiveRange = computed<VitalsRange>(() => {
    const r = this.range();
    return VALID_RANGES.includes(r as VitalsRange) ? (r as VitalsRange) : DEFAULT_RANGE;
  });

  protected readonly rangeOptions = RANGE_OPTIONS;
  protected readonly thresholds = VITAL_THRESHOLDS;

  private readonly refreshTick = signal(0);
  protected readonly lastSaveResult = signal<VitalsSaveResult | null>(null);
  protected readonly saveMessageSeverity = computed<'warn' | 'error'>(() =>
    this.lastSaveResult()?.anomalies.some((a) => a.severity === 'critical') ? 'error' : 'warn',
  );
  protected readonly saveMessageText = computed(() => {
    const anomalies = this.lastSaveResult()?.anomalies ?? [];
    return anomalies.length > 0
      ? `Wykryto odchylenia: ${anomalies.map((a) => a.message).join(' ')}`
      : '';
  });

  private readonly params = computed(() => ({
    patientId: this.patientId(),
    range: this.effectiveRange(),
    tick: this.refreshTick(),
  }));

  protected readonly history = toSignal(
    toObservable(this.params).pipe(
      switchMap((p) => this.vitalsService.getVitals(p.patientId, p.range)),
    ),
    { initialValue: [] as VitalSigns[] },
  );

  protected readonly selectedA = signal<string | null>(null);
  protected readonly selectedB = signal<string | null>(null);

  protected readonly measurementOptions = computed(() =>
    [...this.history()]
      .reverse()
      .map((v) => ({ label: new Date(v.recordedAt).toLocaleString('pl-PL'), value: v.id })),
  );

  protected readonly measurementA = computed(() => {
    const id = this.selectedA() ?? this.defaultAId();
    return this.history().find((v) => v.id === id);
  });
  protected readonly measurementB = computed(() => {
    const id = this.selectedB() ?? this.defaultBId();
    return this.history().find((v) => v.id === id);
  });

  private defaultAId(): string | null {
    const h = this.history();
    return h.length >= 2 ? h[h.length - 2].id : null;
  }
  private defaultBId(): string | null {
    const h = this.history();
    return h.length >= 1 ? h[h.length - 1].id : null;
  }

  protected readonly bpSeries = computed<TrendSeries[]>(() => [
    toSeries('Skurczowe', 'systolic', this.history()),
    toSeries('Rozkurczowe', 'diastolic', this.history()),
  ]);
  protected readonly hrSeries = computed<TrendSeries[]>(() => [
    toSeries('Tętno', 'heartRate', this.history()),
  ]);
  protected readonly tempSeries = computed<TrendSeries[]>(() => [
    toSeries('Temperatura', 'temperature', this.history()),
  ]);
  protected readonly spo2Series = computed<TrendSeries[]>(() => [
    toSeries('SpO₂', 'spo2', this.history()),
  ]);
  protected readonly rrSeries = computed<TrendSeries[]>(() => [
    toSeries('Oddechy', 'respiratoryRate', this.history()),
  ]);

  protected readonly historyRows = computed<HistoryRow[]>(() =>
    [...this.history()].reverse().map((v) => ({
      id: v.id,
      recordedAt: v.recordedAt,
      context: v.context,
      systolic: v.systolic ?? null,
      diastolic: v.diastolic ?? null,
      heartRate: v.heartRate ?? null,
      temperature: v.temperature ?? null,
      spo2: v.spo2 ?? null,
      respiratoryRate: v.respiratoryRate ?? null,
      anomalyLevels: anomalyLevelsOf(v),
    })),
  );

  protected readonly historyColumns: TableColumn<HistoryRow>[] = [
    { field: 'recordedAt', header: 'Data', type: 'datetime', sortable: true },
    { field: 'systolic', header: 'RR sk.' },
    { field: 'diastolic', header: 'RR rozk.' },
    { field: 'heartRate', header: 'Tętno' },
    { field: 'temperature', header: 'Temp.' },
    { field: 'spo2', header: 'SpO₂' },
    { field: 'respiratoryRate', header: 'Oddechy' },
  ];

  protected onRangeChange(value: VitalsRange): void {
    this.router.navigate([], { queryParams: { range: value }, queryParamsHandling: 'merge' });
  }

  protected onSaved(result: VitalsSaveResult): void {
    this.lastSaveResult.set(result);
    this.refreshTick.update((n) => n + 1);
  }
}
