import { labResultServiceStub } from '../../testing/lab-result-service.stub';
import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { MessageService } from 'primeng/api';
import { ResultDetailPage } from './result-detail-page';
import { LabResultService } from '../../services/lab-result.service';

describe('ResultDetailPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), MessageService, labResultServiceStub],
    });
  });

  it('renders a lab result with its observations', async () => {
    const labResultService = TestBed.inject(LabResultService);
    const results = await firstValueFrom(labResultService.getResults('pat-001'));
    const first = results[0];

    const fixture = TestBed.createComponent(ResultDetailPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('resultId', first.id);
    fixture.componentRef.setInput('kind', 'lab');
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain(first.testName);
  });
});
