import { drugServiceStub } from '../../testing/drug-service.stub';
import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import type { Drug } from '../../models';
import { DrugPicker } from './drug-picker';

describe('DrugPicker', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [drugServiceStub],
    });
  });

  it('creates', async () => {
    const fixture = TestBed.createComponent(DrugPicker);
    await fixture.whenStable();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('emits drugSelected when onSelect is called', () => {
    const fixture = TestBed.createComponent(DrugPicker);
    const drug: Drug = {
      id: 'drg-001',
      name: 'Polpril',
      activeSubstance: 'Ramipril',
      atcCode: 'C09AA05',
      form: 'tablet',
      strength: '5 mg',
      packageSize: 28,
      packageUnit: 'tabl.',
      routes: ['oral'],
      defaultDoseUnit: 'mg',
      rxOnly: true,
      reimbursementOptions: ['30%'],
    };
    let emitted: Drug | null = null;
    fixture.componentInstance.drugSelected.subscribe((d) => (emitted = d));
    fixture.componentInstance['onSelect'](drug);
    expect(emitted).toEqual(drug);
  });
});
