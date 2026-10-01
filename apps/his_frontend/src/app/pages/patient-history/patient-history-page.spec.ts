import { patientServiceStub } from '../../testing/patient-service.stub';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { staffServiceStub } from '../../testing/staff-service.stub';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { throwError } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { EhrService } from '../../services/ehr.service';
import { PatientHistoryPage } from './patient-history-page';

// jsdom has no ResizeObserver; PrimeNG's p-tabs relies on it in ngAfterViewInit.
class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

describe('PatientHistoryPage', () => {
  let permissions: string[];

  beforeEach(() => {
    permissions = ['ehr:read', 'ehr:read-limited'];
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: { hasPermission: (p: string) => permissions.includes(p) },
        },
        patientServiceStub,
        ehrServiceStub,
        wardServiceStub,
        staffServiceStub,
        provideRouter([]),
        MessageService,
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

  it('with ehr:read-limited requests and shows only the limited sections', async () => {
    permissions = ['ehr:read-limited'];
    const ehr = TestBed.inject(EhrService);
    const spies = {
      summary: vi.spyOn(ehr, 'getSummary'),
      encounters: vi.spyOn(ehr, 'getEncounters'),
      episodes: vi.spyOn(ehr, 'getEpisodes'),
      notes: vi.spyOn(ehr, 'getNotes'),
      diagnoses: vi.spyOn(ehr, 'getDiagnoses'),
    };
    const fixture = TestBed.createComponent(PatientHistoryPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    expect(spies.summary).not.toHaveBeenCalled();
    expect(spies.encounters).not.toHaveBeenCalled();
    expect(spies.episodes).not.toHaveBeenCalled();
    expect(spies.notes).not.toHaveBeenCalled();
    expect(spies.diagnoses).toHaveBeenCalled();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Notatki kliniczne');
    expect(text).toContain('Rozpoznania');
    expect(fixture.componentInstance['activeTab']()).toBe('diagnoses');
  });

  it('keeps other sections when one request fails', async () => {
    const ehr = TestBed.inject(EhrService);
    vi.spyOn(ehr, 'getNotes').mockReturnValue(throwError(() => new Error('403')));
    const toast = vi.spyOn(TestBed.inject(MessageService), 'add');
    const fixture = TestBed.createComponent(PatientHistoryPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const c = fixture.componentInstance as unknown as {
      notes(): unknown[];
      diagnoses(): unknown[];
    };
    expect(c.notes()).toEqual([]);
    expect(c.diagnoses().length).toBeGreaterThan(0);
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'warn' }));
  });
});
