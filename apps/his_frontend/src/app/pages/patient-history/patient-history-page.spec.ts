import { patientServiceStub } from '../../testing/patient-service.stub';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { staffServiceStub } from '../../testing/staff-service.stub';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';
import { PatientHistoryPage } from './patient-history-page';

// jsdom has no ResizeObserver; PrimeNG's p-tabs relies on it in ngAfterViewInit.
class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

describe('PatientHistoryPage', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    TestBed.configureTestingModule({
      providers: [
        patientServiceStub,
        ehrServiceStub,
        wardServiceStub,
        staffServiceStub,
        provideRouter([]),
        MessageService,
        { provide: MOCK_LATENCY_MS, useValue: 0 },
      ],
    });
  });

  it('renders the page header and tabs', async () => {
    const fixture = TestBed.createComponent(PatientHistoryPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Historia choroby');
    expect(text).toContain('Przegląd');
    expect(text).toContain('Rozpoznania');
  });

  it('defaults the active tab to "overview" when no tab query param is given', async () => {
    const fixture = TestBed.createComponent(PatientHistoryPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect(fixture.componentInstance['activeTab']()).toBe('overview');
  });

  it('falls back to "overview" for an invalid tab query param', async () => {
    const fixture = TestBed.createComponent(PatientHistoryPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('tab', 'not-a-real-tab');
    await fixture.whenStable();
    expect(fixture.componentInstance['activeTab']()).toBe('overview');
  });

  it('accepts a valid tab query param', async () => {
    const fixture = TestBed.createComponent(PatientHistoryPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('tab', 'diagnoses');
    await fixture.whenStable();
    expect(fixture.componentInstance['activeTab']()).toBe('diagnoses');
  });

  it('opens the clinical note dialog when "Dodaj notatkę" is triggered', async () => {
    const fixture = TestBed.createComponent(PatientHistoryPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('tab', 'notes');
    await fixture.whenStable();
    const component = fixture.componentInstance;
    expect(component['noteDialogVisible']()).toBe(false);
    component['openNoteDialog']();
    expect(component['noteDialogVisible']()).toBe(true);
  });
});
