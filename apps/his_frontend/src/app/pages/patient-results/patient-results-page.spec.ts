import { labResultServiceStub } from '../../testing/lab-result-service.stub';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { MessageService } from 'primeng/api';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';
import { PatientResultsPage } from './patient-results-page';
import { LabResultService } from '../../services/lab-result.service';

// jsdom has no ResizeObserver; PrimeNG's p-tabs relies on it in ngAfterViewInit.
class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

describe('PatientResultsPage', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        MessageService,
        labResultServiceStub,
        { provide: MOCK_LATENCY_MS, useValue: 0 },
      ],
    });
  });

  it('renders the lab results tab with the patient’s lab result names', async () => {
    const labResultService = TestBed.inject(LabResultService);
    const results = await firstValueFrom(labResultService.getResults('pat-001'));

    const fixture = TestBed.createComponent(PatientResultsPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Wyniki badań');
    expect(text).toContain('Laboratoryjne');
    expect(text).toContain('Obrazowe');
    expect(results.length).toBeGreaterThan(0);
    expect(text).toContain(results[0].testName);
  });
});
