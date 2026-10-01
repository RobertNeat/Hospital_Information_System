import { describe, expect, it, beforeEach } from 'vitest';
import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { MessageService } from 'primeng/api';
import { vitalsServiceStub } from '../../testing/vitals-service.stub';
import { VitalsService } from '../../services/vitals.service';
import { PatientVitalsPage } from './patient-vitals-page';
import {
  TrendChartCard,
  type TrendSeries,
} from '../../components/trend-chart-card/trend-chart-card';

// jsdom has no canvas 2D context, so chart.js's `new Chart()` throws when it
// tries to acquire one. Swap the real chart for a no-op stub with the same
// selector/inputs rather than exercising chart.js in a DOM it can't support.
@Component({ selector: 'app-trend-chart-card', template: '' })
class TrendChartCardStub {
  readonly title = input.required<string>();
  readonly unit = input('');
  readonly series = input.required<TrendSeries[]>();
  readonly referenceRange = input<{ low?: number; high?: number }>();
  readonly height = input(220);
}

describe('PatientVitalsPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), MessageService, vitalsServiceStub],
    });
    TestBed.overrideComponent(PatientVitalsPage, {
      remove: { imports: [TrendChartCard] },
      add: { imports: [TrendChartCardStub] },
    });
  });

  it('renders the page header and loads the vitals history for the patient', async () => {
    const fixture = TestBed.createComponent(PatientVitalsPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('range', 'all');
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Parametry życiowe');
    expect(fixture.componentInstance['history']().length).toBeGreaterThan(0);
  });

  it('falls back to the default range when the router leaves `range` unset (withComponentInputBinding calls setInput(undefined) for absent query params)', async () => {
    const fixture = TestBed.createComponent(PatientVitalsPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    // Deliberately not setting `range`, mirroring a plain navigation with no ?range= param.
    await fixture.whenStable();

    expect(fixture.componentInstance['effectiveRange']()).toBe('7d');
    expect(fixture.componentInstance['history']().length).toBeGreaterThan(0);
  });

  it('falls back to the default range for an invalid `range` value', async () => {
    const fixture = TestBed.createComponent(PatientVitalsPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('range', 'bogus');
    await fixture.whenStable();

    expect(fixture.componentInstance['effectiveRange']()).toBe('7d');
  });

  it('defaults the compare selection to the latest vs previous measurement', async () => {
    const fixture = TestBed.createComponent(PatientVitalsPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('range', 'all');
    await fixture.whenStable();

    const history = fixture.componentInstance['history']();
    expect(fixture.componentInstance['measurementB']()?.id).toBe(history.at(-1)?.id);
    expect(fixture.componentInstance['measurementA']()?.id).toBe(history.at(-2)?.id);
  });

  it('refreshes the history after a measurement is saved', async () => {
    const fixture = TestBed.createComponent(PatientVitalsPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('range', 'all');
    await fixture.whenStable();

    const before = fixture.componentInstance['history']().length;

    // Actually persist through the service (mirrors what VitalsEntryForm does internally)
    // rather than fabricating a result, so this test would fail if addVitals stopped
    // actually saving.
    const vitalsService = TestBed.inject(VitalsService);
    const result = await firstValueFrom(
      vitalsService.addVitals({
        patientId: 'pat-001',
        context: 'ward_round',
        heartRate: 72,
      }),
    );
    fixture.componentInstance['onSaved'](result);
    await fixture.whenStable();

    expect(fixture.componentInstance['history']().length).toBe(before + 1);
  });
});
