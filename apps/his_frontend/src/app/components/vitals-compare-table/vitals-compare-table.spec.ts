import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import type { VitalSigns } from '../../models';
import { VitalsCompareTable } from './vitals-compare-table';

const base: Omit<VitalSigns, 'temperature' | 'spo2' | 'systolic'> = {
  id: 'vit-1',
  patientId: 'pat-001',
  recordedAt: '2026-01-01T08:00:00.000Z',
  recordedById: 'stf-001',
  context: 'ward_round',
};

describe('VitalsCompareTable', () => {
  it('colors a temperature rising further above normal as danger', async () => {
    const fixture = TestBed.createComponent(VitalsCompareTable);
    fixture.componentRef.setInput('measurementA', { ...base, temperature: 37.6 } as VitalSigns);
    fixture.componentRef.setInput('measurementB', { ...base, temperature: 39.0 } as VitalSigns);
    await fixture.whenStable();

    const rows = fixture.componentInstance['rows']();
    const temp = rows.find((r) => r.type === 'temperature');
    expect(temp?.arrow).toBe('up');
    expect(temp?.arrowSeverity).toBe('danger');
  });

  it('colors a hypotensive systolic recovering toward normal (75 -> 100) as success, not danger', async () => {
    const fixture = TestBed.createComponent(VitalsCompareTable);
    fixture.componentRef.setInput('measurementA', { ...base, systolic: 75 } as VitalSigns);
    fixture.componentRef.setInput('measurementB', { ...base, systolic: 100 } as VitalSigns);
    await fixture.whenStable();

    const rows = fixture.componentInstance['rows']();
    const sys = rows.find((r) => r.type === 'systolic');
    expect(sys?.arrow).toBe('up');
    expect(sys?.arrowSeverity).toBe('success');
  });

  it('colors SpO2 rising toward 100 as success', async () => {
    const fixture = TestBed.createComponent(VitalsCompareTable);
    fixture.componentRef.setInput('measurementA', { ...base, spo2: 88 } as VitalSigns);
    fixture.componentRef.setInput('measurementB', { ...base, spo2: 95 } as VitalSigns);
    await fixture.whenStable();

    const rows = fixture.componentInstance['rows']();
    const spo2 = rows.find((r) => r.type === 'spo2');
    expect(spo2?.arrow).toBe('up');
    expect(spo2?.arrowSeverity).toBe('success');
  });

  it('marks an unchanged in-range value as flat/secondary', async () => {
    const fixture = TestBed.createComponent(VitalsCompareTable);
    fixture.componentRef.setInput('measurementA', { ...base, temperature: 36.8 } as VitalSigns);
    fixture.componentRef.setInput('measurementB', { ...base, temperature: 36.8 } as VitalSigns);
    await fixture.whenStable();

    const rows = fixture.componentInstance['rows']();
    const temp = rows.find((r) => r.type === 'temperature');
    expect(temp?.arrow).toBe('flat');
    expect(temp?.delta).toBe(0);
  });

  it('rounds the temperature delta to 1 decimal to avoid floating-point noise', async () => {
    const fixture = TestBed.createComponent(VitalsCompareTable);
    fixture.componentRef.setInput('measurementA', { ...base, temperature: 36.8 } as VitalSigns);
    fixture.componentRef.setInput('measurementB', { ...base, temperature: 37.2 } as VitalSigns);
    await fixture.whenStable();

    const rows = fixture.componentInstance['rows']();
    const temp = rows.find((r) => r.type === 'temperature');
    expect(temp?.delta).toBe(0.4);
  });
});
