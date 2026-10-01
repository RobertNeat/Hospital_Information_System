import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import type { Observable } from 'rxjs';
import {
  ICD10_URL,
  patientAllergiesUrl,
  patientClinicalNotesUrl,
  patientContraindicationsUrl,
  patientDiagnosesUrl,
  patientEhrSummaryUrl,
  patientEncountersUrl,
  patientEpisodesUrl,
  patientTreatmentsUrl,
} from '../config/api.config';
import type { ClinicalNoteCreateRequest } from '../models/api';
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

  it('getIcd10Dictionary requests the maximum page and an optional term', async () => {
    const result = firstValueFrom(service.getIcd10Dictionary('cuk'));
    const req = http.expectOne((r) => r.url === ICD10_URL);
    expect(req.request.params.get('term')).toBe('cuk');
    expect(req.request.params.get('size')).toBe('100');
    req.flush([{ system: 'ICD-10', code: 'E11', display: 'Cukrzyca' }]);
    expect(await result).toHaveLength(1);
  });
});
