import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import type { ResultFlag, TrendPoint } from '../../models';

export interface TrendSeries {
  label: string;
  points: TrendPoint[];
  color?: string;
}

const DEFAULT_COLORS = ['#1e90ff', '#f59e0b', '#22c55e', '#ef4444'];
const ABNORMAL_FLAGS: ResultFlag[] = ['L', 'H', 'LL', 'HH', 'A'];

/**
 * ng2-charts line chart card. Registers chart.js at the component level (not in
 * app.config.ts) so chart.js is only pulled into the lazy chunks that use this card.
 */
@Component({
  selector: 'app-trend-chart-card',
  imports: [BaseChartDirective],
  providers: [provideCharts(withDefaultRegisterables())],
  templateUrl: './trend-chart-card.html',
  styleUrl: './trend-chart-card.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TrendChartCard {
  readonly title = input.required<string>();
  readonly unit = input('');
  readonly series = input.required<TrendSeries[]>();
  readonly referenceRange = input<{ low?: number; high?: number }>();
  readonly height = input(220);

  protected readonly chartData = computed<ChartData<'line'>>(() => {
    const allLabels = new Set<string>();
    for (const s of this.series()) {
      for (const p of s.points) allLabels.add(p.at);
    }
    const labels = Array.from(allLabels).sort();
    const range = this.referenceRange();

    const seriesDatasets = this.series().map((s, i) => {
      const color = s.color ?? DEFAULT_COLORS[i % DEFAULT_COLORS.length];
      const byLabel = new Map(s.points.map((p) => [p.at, p]));
      return {
        label: s.label,
        data: labels.map((l) => byLabel.get(l)?.value ?? null),
        borderColor: color,
        backgroundColor: color,
        spanGaps: true,
        pointBackgroundColor: labels.map((l) => {
          const flag = byLabel.get(l)?.flag;
          return flag && ABNORMAL_FLAGS.includes(flag) ? '#ef4444' : color;
        }),
      };
    });

    // Draw the reference range as a shaded band without the (unapproved,
    // not-installed) chartjs-plugin-annotation: two flat, non-interactive line
    // datasets (low/high) with `fill: '+1'`/`fill: false` shade the area between them.
    const bandDatasets =
      range && range.low !== undefined && range.high !== undefined
        ? [
            {
              label: 'Zakres referencyjny (dolny)',
              data: labels.map(() => range.low as number),
              borderWidth: 0,
              pointRadius: 0,
              fill: '+1' as const,
              backgroundColor: 'rgba(34, 197, 94, 0.12)',
              order: 10,
            },
            {
              label: 'Zakres referencyjny (górny)',
              data: labels.map(() => range.high as number),
              borderWidth: 0,
              pointRadius: 0,
              fill: false,
              order: 10,
            },
          ]
        : [];

    return { labels, datasets: [...bandDatasets, ...seriesDatasets] };
  });

  protected readonly chartOptions = computed<ChartConfiguration<'line'>['options']>(() => ({
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: this.series().length > 1 },
    },
    scales: {
      y: { title: { display: !!this.unit(), text: this.unit() } },
    },
  }));
}
