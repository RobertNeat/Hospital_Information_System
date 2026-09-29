import { describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { PatientSearch } from './patient-search';
import { PatientService } from '../../services/patient.service';
import type { PatientSummary } from '../../models';

const SUMMARY: PatientSummary = {
  id: 'pat-001',
  mrn: 'HIS/2026/000001',
  pesel: '90010112345',
  firstName: 'Jan',
  lastName: 'Kowalski',
  birthDate: '1990-01-01',
  gender: 'male',
  status: 'admitted',
  flags: [],
};

describe('PatientSearch', () => {
  it('calls PatientService.search and emits the selected patient', async () => {
    const searchSpy = vi.fn().mockReturnValue(of([SUMMARY]));
    TestBed.configureTestingModule({
      providers: [{ provide: PatientService, useValue: { search: searchSpy } }],
    });

    const fixture = TestBed.createComponent(PatientSearch);
    await fixture.whenStable();

    let emitted: PatientSummary | undefined;
    fixture.componentInstance.patientSelected.subscribe((p) => (emitted = p));

    (fixture.componentInstance as unknown as { search: (e: { query: string }) => void }).search({
      query: 'Kowalski',
    });
    expect(searchSpy).toHaveBeenCalledWith('Kowalski');

    (
      fixture.componentInstance as unknown as { onSelect: (e: { value: PatientSummary }) => void }
    ).onSelect({ value: SUMMARY });
    expect(emitted).toEqual(SUMMARY);
  });
});
