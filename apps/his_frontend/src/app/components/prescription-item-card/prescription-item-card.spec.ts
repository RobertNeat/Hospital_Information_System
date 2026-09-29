import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import type { PrescriptionItem } from '../../models';
import { PrescriptionItemCard } from './prescription-item-card';

const ITEM: PrescriptionItem = {
  drugId: 'drg-001',
  drugName: 'Polpril',
  activeSubstance: 'Ramipril',
  strength: '5 mg',
  form: 'tablet',
  dosage: {
    dose: 5,
    doseUnit: 'mg',
    route: 'oral',
    frequency: 'QD',
    durationDays: 30,
    asNeeded: false,
  },
  quantityPackages: 1,
  reimbursement: '30%',
  substitutionAllowed: true,
};

describe('PrescriptionItemCard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('renders the drug name', async () => {
    const fixture = TestBed.createComponent(PrescriptionItemCard);
    fixture.componentRef.setInput('item', ITEM);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Polpril');
  });

  it('emits remove()', async () => {
    const fixture = TestBed.createComponent(PrescriptionItemCard);
    fixture.componentRef.setInput('item', ITEM);
    await fixture.whenStable();
    let removed = false;
    fixture.componentInstance.remove.subscribe(() => (removed = true));
    fixture.componentInstance.remove.emit();
    expect(removed).toBe(true);
  });
});
