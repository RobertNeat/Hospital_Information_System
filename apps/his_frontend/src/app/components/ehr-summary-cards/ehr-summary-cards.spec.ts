import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import type { EhrSummary, Encounter } from '../../models';
import { EhrSummaryCards } from './ehr-summary-cards';

function makeSummary(overrides: Partial<EhrSummary> = {}): EhrSummary {
  return {
    recentDiagnoses: [
      {
        id: 'diag-1',
        patientId: 'pat-001',
        code: { system: 'SNOMED', code: '38341003', display: 'Nadciśnienie tętnicze samoistne' },
        type: 'primary',
        status: 'active',
        diagnosedAt: '2026-01-01T08:00:00.000Z',
        diagnosedById: 'stf-001',
      },
    ],
    chronicConditions: [],
    activeMedications: [],
    recentEncounters: [],
    allergies: [],
    ...overrides,
  };
}

describe('EhrSummaryCards', () => {
  it('renders recent diagnoses in the summary table', async () => {
    const fixture = TestBed.createComponent(EhrSummaryCards);
    fixture.componentRef.setInput('summary', makeSummary());
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('38341003');
    expect(text).toContain('Nadciśnienie tętnicze samoistne');
  });

  it('shows the "Wizyta w trakcie" card only for an in_progress encounter', async () => {
    const fixture = TestBed.createComponent(EhrSummaryCards);
    fixture.componentRef.setInput('summary', makeSummary());
    const inProgress: Encounter = {
      id: 'enc-1',
      patientId: 'pat-001',
      type: 'visit',
      status: 'in_progress',
      startAt: '2026-01-01T08:00:00.000Z',
      practitionerId: 'stf-001',
      reason: 'Kontrola',
    };
    fixture.componentRef.setInput('encounters', [inProgress]);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Wizyta w trakcie');
  });

  it('does not show the "Wizyta w trakcie" card when no encounter is in progress', async () => {
    const fixture = TestBed.createComponent(EhrSummaryCards);
    fixture.componentRef.setInput('summary', makeSummary());
    fixture.componentRef.setInput('encounters', []);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Wizyta w trakcie');
  });

  it('shows empty states when there is no data', async () => {
    const fixture = TestBed.createComponent(EhrSummaryCards);
    fixture.componentRef.setInput(
      'summary',
      makeSummary({ recentDiagnoses: [], chronicConditions: [] }),
    );
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Brak rozpoznań');
    expect(text).toContain('Brak chorób przewlekłych');
  });
});
