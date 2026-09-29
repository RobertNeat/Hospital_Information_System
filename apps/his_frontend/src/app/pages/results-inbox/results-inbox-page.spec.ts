import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';
import { ResultsInboxPage } from './results-inbox-page';
import { LabResultService } from '../../services/lab-result.service';

describe('ResultsInboxPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: MOCK_LATENCY_MS, useValue: 0 }],
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
});
