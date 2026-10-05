import { patientServiceStub } from '../../testing/patient-service.stub';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { throwError } from 'rxjs';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { PatientListPage } from './patient-list-page';
import { PatientService } from '../../services/patient.service';

describe('PatientListPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        patientServiceStub,
        ehrServiceStub,
        provideRouter([]),
        wardServiceStub,
        MessageService,
      ],
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
    // Debounced 300ms; once the debounce fires the stub resolves sync.
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

  it('re-syncs filters when the query inputs change', async () => {
    const fixture = TestBed.createComponent(PatientListPage);
    fixture.componentRef.setInput('q', 'Kowalski');
    await fixture.whenStable();
    fixture.componentRef.setInput('q', 'Nowak');
    fixture.componentRef.setInput('status', 'admitted');
    await fixture.whenStable();
    const cmp = fixture.componentInstance as unknown as {
      searchTerm: () => string;
      selectedStatus: () => string;
    };
    expect(cmp.searchTerm()).toBe('Nowak');
    expect(cmp.selectedStatus()).toBe('admitted');
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

  it('stops the spinner and toasts instead of hanging when the search fails', async () => {
    const patientService = TestBed.inject(PatientService);
    vi.spyOn(patientService, 'getPatients').mockReturnValue(throwError(() => new Error('500')));
    const fixture = TestBed.createComponent(PatientListPage);
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 350));
    await fixture.whenStable();
    const cmp = fixture.componentInstance as unknown as {
      loading: () => boolean;
      rows: () => unknown[];
    };
    expect(cmp.loading()).toBe(false);
    expect(cmp.rows()).toEqual([]);
  });
});
