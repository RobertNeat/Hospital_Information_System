import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { Select } from 'primeng/select';
import { DatePicker } from 'primeng/datepicker';
import { LabResultService } from '../../services/lab-result.service';
import { TrendChartCard, type TrendSeries } from '../trend-chart-card/trend-chart-card';
import type { AnalyteTrend, ID, SelectOption } from '../../models';

interface CompareRow {
  at: string;
  value: number;
  delta: number | null;
  deltaPercent: number | null;
}

/**
 * "Porównaj wyniki w czasie" dialog: pick an analyte and a date range, show a
 * trend chart with the reference band plus a table of consecutive deltas.
 */
@Component({
  selector: 'app-result-compare-dialog',
  imports: [Dialog, Select, DatePicker, DatePipe, FormsModule, TrendChartCard],
  templateUrl: './result-compare-dialog.html',
  styleUrl: './result-compare-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'result-compare-dialog' },
})
export class ResultCompareDialog {
  private readonly labResultService = inject(LabResultService);

  readonly patientId = input.required<ID>();
  readonly visible = input.required<boolean>();
  readonly analyteOptions = input<SelectOption[]>([]);
  readonly initialAnalyteCode = input<string>();

  readonly visibleChange = output<boolean>();

  protected readonly selectedAnalyte = signal<string | undefined>(undefined);
  protected readonly dateFrom = signal<Date | null>(null);
  protected readonly dateTo = signal<Date | null>(null);

  protected readonly analyteCode = computed(
    () => this.selectedAnalyte() ?? this.initialAnalyteCode(),
  );

  private readonly trendRequest = toObservable(
    computed(() => ({ pid: this.patientId(), code: this.analyteCode() })),
  ).pipe(
    switchMap(({ pid, code }) => {
      if (!code) return [undefined];
      return this.labResultService.getAnalyteTrend(pid, code);
    }),
  );

  protected readonly trend = toSignal<AnalyteTrend | undefined>(this.trendRequest, {
    initialValue: undefined,
  });

  protected readonly filteredPoints = computed(() => {
    const trend = this.trend();
    if (!trend) return [];
    const from = this.dateFrom();
    const to = this.dateTo();
    // `to` is end-of-day inclusive: a bare DatePicker date defaults to midnight, which
    // would otherwise exclude every result collected later that same day.
    const toEndOfDay = to
      ? new Date(to.getFullYear(), to.getMonth(), to.getDate(), 23, 59, 59, 999)
      : null;
    return trend.points.filter((p) => {
      const at = new Date(p.at).getTime();
      if (from && at < from.getTime()) return false;
      if (toEndOfDay && at > toEndOfDay.getTime()) return false;
      return true;
    });
  });

  protected readonly series = computed<TrendSeries[]>(() => {
    const trend = this.trend();
    if (!trend) return [];
    return [{ label: trend.analyteName, points: this.filteredPoints() }];
  });

  protected readonly referenceRange = computed(() => {
    const trend = this.trend();
    return trend ? { low: trend.low, high: trend.high } : undefined;
  });

  protected readonly compareRows = computed<CompareRow[]>(() => {
    const points = this.filteredPoints();
    return points.map((p, i) => {
      if (i === 0) return { at: p.at, value: p.value, delta: null, deltaPercent: null };
      const prev = points[i - 1].value;
      const delta = p.value - prev;
      const deltaPercent = prev !== 0 ? (delta / prev) * 100 : null;
      return { at: p.at, value: p.value, delta, deltaPercent };
    });
  });

  protected onSelectAnalyte(code: string | undefined): void {
    this.selectedAnalyte.set(code);
  }

  protected onHide(): void {
    this.visibleChange.emit(false);
  }
}
