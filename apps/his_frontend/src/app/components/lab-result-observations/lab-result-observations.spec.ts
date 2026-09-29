import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { LabResultObservations } from './lab-result-observations';
import type { LabObservation } from '../../models';

describe('LabResultObservations', () => {
  const observations: LabObservation[] = [
    {
      analyteCode: 'HGB',
      analyteName: 'Hemoglobina',
      value: 9.5,
      unit: 'g/dL',
      referenceRange: { low: 12, high: 16 },
      flag: 'LL',
    },
    {
      analyteCode: 'WBC',
      analyteName: 'Leukocyty',
      value: 7.2,
      unit: 'tys/uL',
      referenceRange: { low: 4, high: 10 },
      flag: 'N',
    },
  ];

  it('renders one row per observation with its Polish flag label', async () => {
    const fixture = TestBed.createComponent(LabResultObservations);
    fixture.componentRef.setInput('observations', observations);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Hemoglobina');
    expect(text).toContain('Krytycznie niskie');
    expect(text).toContain('W normie');
  });
});
