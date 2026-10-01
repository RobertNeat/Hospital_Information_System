import { imagingResultServiceStub } from '../../testing/imaging-result-service.stub';
import { labResultServiceStub } from '../../testing/lab-result-service.stub';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom, of } from 'rxjs';
import { ResultsInboxPage } from './results-inbox-page';
import { LabResultService } from '../../services/lab-result.service';
import { ImagingResultService } from '../../services/imaging-result.service';
import { IMAGING_RESULTS } from '../../mock-data/imaging-results.mock';

describe('ResultsInboxPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), labResultServiceStub, imagingResultServiceStub],
    });
  });

  it('renders results across patients with a patient link column', async () => {
    const fixture = TestBed.createComponent(ResultsInboxPage);
    fixture.componentRef.setInput('filter', 'all');
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Wyniki badań');
  });

  it('defaults an unset filter query param to "all" instead of falling through to "abnormal"', async () => {
    const labResultService = TestBed.inject(LabResultService);
    const [allResults, abnormalResults] = await Promise.all([
      firstValueFrom(labResultService.getRecent('all')),
      firstValueFrom(labResultService.getRecent('abnormal')),
    ]);
    // Sanity check on the fixture: the "all" filter must show strictly more results
    // than "abnormal" for this assertion to be meaningful.
    expect(allResults.length).toBeGreaterThan(abnormalResults.length);

    const fixture = TestBed.createComponent(ResultsInboxPage);
    fixture.componentRef.setInput('filter', undefined);
    await fixture.whenStable();
    // Assert against the underlying rows signal rather than rendered text: the
    // table paginates at 10 rows, so a specific result may not be on the first
    // rendered page even though it's present in the unfiltered "all" dataset.
    const rows = fixture.componentInstance['rows']();
    const normalResult = allResults.find((r) => !abnormalResults.some((a) => a.id === r.id));
    expect(normalResult).toBeDefined();
    expect(rows.length).toBe(allResults.length);
    expect(rows.some((r: { id: string }) => r.id === normalResult!.id)).toBe(true);
  });

  it('falls back to the patient id when an inbox row has no patient summary', async () => {
    const result = IMAGING_RESULTS[0];
    vi.spyOn(TestBed.inject(ImagingResultService), 'getRecent').mockReturnValue(of([result]));
    const fixture = TestBed.createComponent(ResultsInboxPage);
    fixture.componentRef.setInput('filter', 'all');
    await fixture.whenStable();
    const rows = fixture.componentInstance['imagingRows']();
    expect(rows).toHaveLength(1);
    expect(rows[0].patientName).toBe(result.patientId);
  });
});
