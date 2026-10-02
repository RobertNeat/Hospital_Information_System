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

  it('shows "Dodaj rozpoznanie" for a doctor (ehr:diagnosis:write)', async () => {
    permissions = ['ehr:read', 'ehr:diagnosis:write'];
    const fixture = TestBed.createComponent(PatientHistoryPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('tab', 'diagnoses');
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Dodaj rozpoznanie');
    expect(text).not.toContain('Dodaj alergię');
  });

  it('shows "Dodaj alergię" but not "Dodaj rozpoznanie" for a nurse (ehr:allergy:write only)', async () => {
    permissions = ['ehr:read-limited', 'ehr:allergy:write'];
    const fixture = TestBed.createComponent(PatientHistoryPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('tab', 'allergies');
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Dodaj alergię');
    expect(text).not.toContain('Dodaj rozpoznanie');
  });

  it('shows neither write button for a pharmacist (ehr:read-limited only)', async () => {
    permissions = ['ehr:read-limited'];
    const fixture = TestBed.createComponent(PatientHistoryPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('tab', 'allergies');
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Dodaj alergię');
    expect(text).not.toContain('Dodaj rozpoznanie');
  });

  it('saves a new diagnosis, shows a success toast and reloads', async () => {
    permissions = ['ehr:read', 'ehr:diagnosis:write'];
    const ehr = TestBed.inject(EhrService);
    const addSpy = vi.spyOn(ehr, 'addDiagnosis');
    const toast = vi.spyOn(TestBed.inject(MessageService), 'add');
    const fixture = TestBed.createComponent(PatientHistoryPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const component = fixture.componentInstance;

    component['onDiagnosisSave']({
      draft: {
        patientId: 'pat-001',
        code: { system: 'SNOMED', code: '44054006', display: 'Cukrzyca typu 2' },
        type: 'primary',
      },
    });
    await fixture.whenStable();

    expect(addSpy).toHaveBeenCalledWith('pat-001', expect.objectContaining({ type: 'primary' }));
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('shows an error toast when saving a diagnosis fails', async () => {
    permissions = ['ehr:read', 'ehr:diagnosis:write'];
    const ehr = TestBed.inject(EhrService);
    vi.spyOn(ehr, 'addDiagnosis').mockReturnValue(throwError(() => new Error('422')));
    const toast = vi.spyOn(TestBed.inject(MessageService), 'add');
    const fixture = TestBed.createComponent(PatientHistoryPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    fixture.componentInstance['onDiagnosisSave']({
      draft: {
        patientId: 'pat-001',
        code: { system: 'SNOMED', code: '44054006', display: 'Cukrzyca typu 2' },
        type: 'primary',
      },
    });
    await fixture.whenStable();

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
  });

  it('saves a new allergy, shows a success toast and reloads', async () => {
    permissions = ['ehr:read-limited', 'ehr:allergy:write'];
    const ehr = TestBed.inject(EhrService);
    const addSpy = vi.spyOn(ehr, 'addAllergy');
    const toast = vi.spyOn(TestBed.inject(MessageService), 'add');
    const fixture = TestBed.createComponent(PatientHistoryPage);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    fixture.componentInstance['onAllergySave']({
      draft: {
        patientId: 'pat-001',
        substance: 'Penicylina',
        category: 'drug',
        reaction: 'Wysypka',
        severity: 'moderate',
      },
    });
    await fixture.whenStable();

    expect(addSpy).toHaveBeenCalledWith(
      'pat-001',
      expect.objectContaining({ substance: 'Penicylina' }),
    );
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
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
