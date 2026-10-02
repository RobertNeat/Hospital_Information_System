import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import type { Observable } from 'rxjs';
import {
  SNOMED_SUGGESTIONS_URL,
  patientAllergiesUrl,
  patientClinicalNotesUrl,
  patientContraindicationsUrl,
  patientDiagnosesUrl,
  patientEhrSummaryUrl,
  patientEncountersUrl,
  patientEpisodesUrl,
  patientTreatmentsUrl,
} from '../config/api.config';
import type {
  AllergyCreateRequest,
  ClinicalNoteCreateRequest,
  DiagnosisCreateRequest,
} from '../models/api';
import { EhrService } from './ehr.service';

describe('EhrService', () => {
  let service: EhrService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EhrService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getSummary returns the backend projection as is', async () => {
    const summary = {
      recentDiagnoses: [],
      chronicConditions: [],
      activeMedications: [],
      recentEncounters: [],
      allergies: [],
    };
    const result = firstValueFrom(service.getSummary('p-1'));
    http.expectOne({ method: 'GET', url: patientEhrSummaryUrl('p-1') }).flush(summary);
    expect(await result).toEqual(summary);
  });

  it.each([
    ['getEncounters', patientEncountersUrl],
    ['getEpisodes', patientEpisodesUrl],
    ['getNotes', patientClinicalNotesUrl],
    ['getDiagnoses', patientDiagnosesUrl],
    ['getAllergies', patientAllergiesUrl],
    ['getContraindications', patientContraindicationsUrl],
    ['getTreatments', patientTreatmentsUrl],
  ] as const)('%s GETs the patient sub-resource', async (method, url) => {
    const result = firstValueFrom(
      (service[method] as (id: string) => Observable<unknown>).call(service, 'p-1'),
    );
    http.expectOne({ method: 'GET', url: url('p-1') }).flush([]);
    expect(await result).toEqual([]);
  });

  it('getAllergies propagates a 403 (no EHR access)', async () => {
    const result = firstValueFrom(service.getAllergies('p-1'));
    http
      .expectOne(patientAllergiesUrl('p-1'))
      .flush(null, { status: 403, statusText: 'Forbidden' });
    await expect(result).rejects.toBeTruthy();
  });

  it('addNote POSTs to the patient of the draft and returns the saved note', async () => {
    const draft: ClinicalNoteCreateRequest = {
      patientId: 'p-1',
      authorId: 's-1',
      category: 'progress',
      title: 'Wizyta',
      content: 'Opis',
    };
    const result = firstValueFrom(service.addNote(draft));
    const req = http.expectOne({ method: 'POST', url: patientClinicalNotesUrl('p-1') });
    expect(req.request.body).toEqual(draft);
    req.flush({ ...draft, id: 'n-1' }, { status: 201, statusText: 'Created' });
    expect((await result).id).toBe('n-1');
  });

  it('addDiagnosis POSTs to the patient diagnoses endpoint and returns the saved diagnosis', async () => {
    const draft: DiagnosisCreateRequest = {
      patientId: 'p-1',
      code: { system: 'SNOMED', code: '44054006', display: 'Cukrzyca typu 2' },
      type: 'primary',
    };
    const result = firstValueFrom(service.addDiagnosis('p-1', draft));
    const req = http.expectOne({ method: 'POST', url: patientDiagnosesUrl('p-1') });
    expect(req.request.body).toEqual(draft);
    req.flush(
      { ...draft, id: 'd-1', status: 'active', diagnosedAt: '2026-01-01T00:00:00Z' },
      { status: 201, statusText: 'Created' },
    );
    expect((await result).id).toBe('d-1');
  });

  it('addDiagnosis propagates a 422 (non-SNOMED or invalid SCTID)', async () => {
    const draft: DiagnosisCreateRequest = {
      patientId: 'p-1',
      code: { system: 'ICD_10' as never, code: 'J45', display: 'Astma' },
      type: 'primary',
    };
    const result = firstValueFrom(service.addDiagnosis('p-1', draft));
    http
      .expectOne({ method: 'POST', url: patientDiagnosesUrl('p-1') })
      .flush(
        { errors: [{ field: 'code.system', message: 'unsupportedSystem' }] },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    await expect(result).rejects.toBeTruthy();
  });

  it('addAllergy POSTs to the patient allergies endpoint and returns the saved allergy', async () => {
    const draft: AllergyCreateRequest = {
      patientId: 'p-1',
      substance: 'Penicylina',
      category: 'drug',
      reaction: 'Wysypka',
      severity: 'moderate',
    };
    const result = firstValueFrom(service.addAllergy('p-1', draft));
    const req = http.expectOne({ method: 'POST', url: patientAllergiesUrl('p-1') });
    expect(req.request.body).toEqual(draft);
    req.flush(
      { ...draft, id: 'a-1', status: 'active', recordedAt: '2026-01-01T00:00:00Z' },
      { status: 201, statusText: 'Created' },
    );
    expect((await result).id).toBe('a-1');
  });

  it('getSnomedSuggestions requests the kind, term and a page size', async () => {
    const result = firstValueFrom(service.getSnomedSuggestions('diagnosis', 'cuk'));
    const req = http.expectOne((r) => r.url === SNOMED_SUGGESTIONS_URL);
    expect(req.request.params.get('kind')).toBe('diagnosis');
    expect(req.request.params.get('term')).toBe('cuk');
    expect(req.request.params.get('size')).toBe('100');
    req.flush({
      total: 1,
      offset: 0,
      concepts: [{ code: '44054006', display: 'Cukrzyca typu 2' }],
    });
    expect((await result).concepts).toHaveLength(1);
  });
});
