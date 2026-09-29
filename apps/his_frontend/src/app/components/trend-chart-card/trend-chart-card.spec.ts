import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { TrendChartCard } from './trend-chart-card';

describe('TrendChartCard', () => {
  beforeEach(() => {
    // jsdom has no canvas 2D context; chart.js only needs getContext() to not throw.
    HTMLCanvasElement.prototype.getContext = () => null;
  });

  it('builds one dataset per series with abnormal points marked red', async () => {
    const fixture = TestBed.createComponent(TrendChartCard);
    fixture.componentRef.setInput('title', 'Glukoza');
    fixture.componentRef.setInput('unit', 'mg/dL');
    fixture.componentRef.setInput('series', [
      {
        label: 'Glukoza',
        points: [
          { at: '2026-01-01', value: 95, flag: 'N' },
          { at: '2026-02-01', value: 210, flag: 'HH' },
        ],
      },
    ]);
    await fixture.whenStable();

    const data = fixture.componentInstance['chartData']();
    expect(data.datasets.length).toBe(1);
    expect(data.datasets[0].data).toEqual([95, 210]);
    expect(
      (data.datasets[0] as unknown as { pointBackgroundColor: string[] }).pointBackgroundColor[1],
    ).toBe('#ef4444');
  });

  it('adds a shaded reference-range band when low/high are provided', async () => {
    const fixture = TestBed.createComponent(TrendChartCard);
    fixture.componentRef.setInput('title', 'Glukoza');
    fixture.componentRef.setInput('series', [
      { label: 'Glukoza', points: [{ at: '2026-01-01', value: 95 }] },
    ]);
    fixture.componentRef.setInput('referenceRange', { low: 70, high: 99 });
    await fixture.whenStable();

    const data = fixture.componentInstance['chartData']();
    expect(data.datasets.length).toBe(3);
  });
});
