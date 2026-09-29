import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { PatientListPage } from './patient-list-page';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';

describe('PatientListPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: MOCK_LATENCY_MS, useValue: 0 }],
    });
  });

  it('renders the page header', async () => {
    const fixture = TestBed.createComponent(PatientListPage);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Pacjenci');
  });

  it('loads patients into the table', async () => {
    const fixture = TestBed.createComponent(PatientListPage);
    await fixture.whenStable();
    // Debounced 300ms; MOCK_LATENCY_MS is 0 so once the debounce fires the mock resolves sync.
    await new Promise((resolve) => setTimeout(resolve, 350));
    await fixture.whenStable();
    const rows = (fixture.componentInstance as unknown as { rows: () => unknown[] }).rows();
    expect(rows.length).toBeGreaterThan(0);
  });

  it('seeds the search term from the q input', async () => {
    const fixture = TestBed.createComponent(PatientListPage);
    fixture.componentRef.setInput('q', 'Kowalski');
    await fixture.whenStable();
    const searchTerm = (
      fixture.componentInstance as unknown as { searchTerm: () => string }
    ).searchTerm();
    expect(searchTerm).toBe('Kowalski');
  });

  it('navigates to the chart when a row is opened', async () => {
    const fixture = TestBed.createComponent(PatientListPage);
    await fixture.whenStable();
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate');
    (
      fixture.componentInstance as unknown as {
        openChart: (row: { id: string }) => void;
      }
    ).openChart({ id: 'pat-001' });
    expect(navigateSpy).toHaveBeenCalledWith(['/patients', 'pat-001']);
  });

  it('navigates to registration when the register button is used', async () => {
    const fixture = TestBed.createComponent(PatientListPage);
    await fixture.whenStable();
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate');
    (fixture.componentInstance as unknown as { goToRegister: () => void }).goToRegister();
    expect(navigateSpy).toHaveBeenCalledWith(['/patients', 'register']);
  });
});
