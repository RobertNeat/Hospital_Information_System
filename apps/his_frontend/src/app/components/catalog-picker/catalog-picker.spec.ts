import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CatalogPicker } from './catalog-picker';
import type { LabTest } from '../../models';

const TESTS: LabTest[] = [
  {
    code: 'MORF',
    name: 'Morfologia krwi z rozmazem',
    category: 'hematology',
    specimenTypes: ['blood'],
    defaultSpecimen: 'blood',
    turnaroundHours: 2,
    fastingRequired: false,
    analytes: [],
  },
  {
    code: 'GLU',
    name: 'Glukoza na czczo',
    category: 'biochemistry',
    specimenTypes: ['serum'],
    defaultSpecimen: 'serum',
    turnaroundHours: 2,
    fastingRequired: true,
    analytes: [],
  },
];

describe('CatalogPicker', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('groups tests by category and renders panel buttons', async () => {
    const fixture = TestBed.createComponent(CatalogPicker);
    fixture.componentRef.setInput('items', TESTS);
    fixture.componentRef.setInput('panels', [
      { id: 'panel-1', name: 'Pakiet przedoperacyjny', testCodes: ['MORF', 'GLU'] },
    ]);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Morfologia krwi z rozmazem');
    expect(text).toContain('Glukoza na czczo');
    expect(text).toContain('Pakiet przedoperacyjny');
  });

  it('adds all panel test codes to selectedCodes when a panel button is clicked', async () => {
    const fixture = TestBed.createComponent(CatalogPicker);
    fixture.componentRef.setInput('items', TESTS);
    fixture.componentRef.setInput('panels', [
      { id: 'panel-1', name: 'Pakiet przedoperacyjny', testCodes: ['MORF', 'GLU'] },
    ]);
    await fixture.whenStable();

    const panelButton = (fixture.nativeElement as HTMLElement).querySelector('p-button button');
    (panelButton as HTMLElement).click();
    await fixture.whenStable();

    expect(fixture.componentInstance.selectedCodes()).toEqual(['MORF', 'GLU']);
  });

  it('toggles a single test checkbox on and off', async () => {
    const fixture = TestBed.createComponent(CatalogPicker);
    fixture.componentRef.setInput('items', TESTS);
    await fixture.whenStable();

    const checkbox = (fixture.nativeElement as HTMLElement).querySelector(
      'p-checkbox input[type="checkbox"]',
    ) as HTMLInputElement | null;
    expect(checkbox).not.toBeNull();

    checkbox?.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.selectedCodes().length).toBe(1);

    checkbox?.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.selectedCodes().length).toBe(0);
  });
});
