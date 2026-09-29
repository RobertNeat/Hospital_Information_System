import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import type { Drug } from '../../models';
import { DosageEditor } from './dosage-editor';

const METFORMAX: Drug = {
  id: 'drg-004',
  name: 'Metformax',
  activeSubstance: 'Metformina',
  atcCode: 'A10BA02',
  form: 'tablet',
  strength: '850 mg',
  packageSize: 30,
  packageUnit: 'tabl.',
  routes: ['oral'],
  defaultDoseUnit: 'mg',
  rxOnly: true,
  reimbursementOptions: ['30%'],
  maxDailyDose: { value: 3000, unit: 'mg' },
};

describe('DosageEditor', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  function buildForm() {
    const fb = TestBed.inject(FormBuilder).nonNullable;
    return fb.group({
      dose: fb.control<number | null>(850),
      doseUnit: fb.control('mg'),
      route: fb.control<string | null>('oral'),
      frequency: fb.control<string | null>('BID'),
      timesOfDay: fb.control<string[]>([]),
      asNeeded: fb.control(false),
      maxPerDay: fb.control<number | null>(null),
      instructions: fb.control(''),
    });
  }

  it('computes the daily dose and warns above maxDailyDose', async () => {
    const fixture = TestBed.createComponent(DosageEditor);
    fixture.componentRef.setInput('form', buildForm());
    fixture.componentRef.setInput('drug', METFORMAX);
    await fixture.whenStable();
    // 850mg BID = 1700mg/day, under 3000mg max
    expect(fixture.componentInstance['dailyDose']()).toBe(1700);
    expect(fixture.componentInstance['exceedsMax']()).toBe(false);
  });

  it('warns when daily dose exceeds maxDailyDose', async () => {
    const fixture = TestBed.createComponent(DosageEditor);
    const form = buildForm();
    form.patchValue({ dose: 2000, frequency: 'BID' });
    fixture.componentRef.setInput('form', form);
    fixture.componentRef.setInput('drug', METFORMAX);
    await fixture.whenStable();
    // 2000mg BID = 4000mg/day, over 3000mg max
    expect(fixture.componentInstance['dailyDose']()).toBe(4000);
    expect(fixture.componentInstance['exceedsMax']()).toBe(true);
  });
});
