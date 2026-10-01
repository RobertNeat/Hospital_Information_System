import { labResultServiceStub } from '../../testing/lab-result-service.stub';
import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';
import { ResultCompareDialog } from './result-compare-dialog';
import { LabResultService } from '../../services/lab-result.service';

describe('ResultCompareDialog', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [labResultServiceStub, { provide: MOCK_LATENCY_MS, useValue: 0 }],
    });
  });

  it('shows a placeholder prompt when no analyte is selected', async () => {
    const fixture = TestBed.createComponent(ResultCompareDialog);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('analyteOptions', [{ label: 'Hemoglobina', value: 'HGB' }]);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Wybierz badanie, aby zobaczyć porównanie w czasie.');
  });

  it('computes a non-zero delta between the first two trend points for a preselected analyte', async () => {
    const labResultService = TestBed.inject(LabResultService);
    const trend = await firstValueFrom(labResultService.getAnalyteTrend('pat-001', 'HGB'));
    expect(trend.points.length).toBeGreaterThan(1);
    const expectedDelta = trend.points[1].value - trend.points[0].value;

    const fixture = TestBed.createComponent(ResultCompareDialog);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('analyteOptions', [{ label: 'Hemoglobina', value: 'HGB' }]);
    fixture.componentRef.setInput('initialAnalyteCode', 'HGB');
    await fixture.whenStable();

    const rows = (
      fixture.componentInstance as unknown as {
        compareRows: () => { delta: number | null }[];
      }
    ).compareRows();
    expect(rows[1].delta).toBeCloseTo(expectedDelta, 5);
  });
});
