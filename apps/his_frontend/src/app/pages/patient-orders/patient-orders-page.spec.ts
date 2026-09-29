import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';
import { PatientOrdersPage } from './patient-orders-page';

// jsdom has no ResizeObserver; PrimeNG's p-tabs relies on it in ngAfterViewInit.
class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

describe('PatientOrdersPage', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        MessageService,
        ConfirmationService,
        { provide: MOCK_LATENCY_MS, useValue: 0 },
      ],
    });
  });

  it('renders the page header and lab orders for the patient', async () => {
    const fixture = TestBed.createComponent(PatientOrdersPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Zlecenia pacjenta');
  });

  it('defaults to the lab tab, showing lab orders and not imaging orders', async () => {
    const fixture = TestBed.createComponent(PatientOrdersPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const activeTab = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-p-active="true"]',
    );
    expect(activeTab?.textContent).toContain('Laboratoryjne');
  });

  it('switches to the imaging tab via the type input', async () => {
    const fixture = TestBed.createComponent(PatientOrdersPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('type', 'imaging');
    await fixture.whenStable();
    const activeTab = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-p-active="true"]',
    );
    expect(activeTab?.textContent).toContain('Obrazowe');
  });
});
