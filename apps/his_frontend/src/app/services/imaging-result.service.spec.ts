import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import {
  IMAGING_RESULTS_URL,
  imagingResultAcknowledgeUrl,
  imagingResultUrl,
  patientImagingResultsUrl,
} from '../config/api.config';
import { IMAGING_RESULTS } from '../mock-data/imaging-results.mock';
import type { ImagingResult, PatientSummary, ResultWithPatient } from '../models';
import type { Page } from '../models/api';
import { ImagingResultService } from './imaging-result.service';

type Row = ResultWithPatient<ImagingResult>;

const row = (result: ImagingResult): Row => ({ ...result, patient: {} as PatientSummary });

const page = (items: Row[], n: number, totalPages: number): Page<Row> => ({
  items,
  page: n,
  size: 100,
  totalElements: items.length,
  totalPages,
});

describe('ImagingResultService', () => {
  let service: ImagingResultService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ImagingResultService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getResults GETs the patient list without a filter by default', async () => {
    const result = firstValueFrom(service.getResults('pat-001'));
    const req = http.expectOne((r) => r.url === patientImagingResultsUrl('pat-001'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([IMAGING_RESULTS[0]]);
    expect(await result).toEqual([IMAGING_RESULTS[0]]);
  });

  it('getResults sends the abnormality filter', () => {
    service.getResults('pat-001', 'critical').subscribe();
    const req = http.expectOne((r) => r.url === patientImagingResultsUrl('pat-001'));
    expect(req.request.params.get('filter')).toBe('critical');
    req.flush([]);
  });

  it('getResultById GETs /imaging-results/{id} and surfaces 404', async () => {
    const result = firstValueFrom(service.getResultById('ires-999'));
    http
      .expectOne({ method: 'GET', url: imagingResultUrl('ires-999') })
      .flush(null, { status: 404, statusText: 'Not Found' });
    await expect(result).rejects.toMatchObject({ status: 404 });
  });

  it('acknowledgeResult POSTs without a version and returns the reviewed result', async () => {
    const reviewed = {
      ...IMAGING_RESULTS[0],
      reviewedAt: '2026-01-01T00:00:00Z',
      reviewedById: 'u1',
    };
    const result = firstValueFrom(service.acknowledgeResult('ires-001'));
    const req = http.expectOne({ method: 'POST', url: imagingResultAcknowledgeUrl('ires-001') });
    expect(req.request.body).toEqual({});
    req.flush(reviewed);
    expect(await result).toEqual(reviewed);
  });

  it('getRecent sends filter and paging to the inbox endpoint', async () => {
    const a = row(IMAGING_RESULTS[0]);
    const result = firstValueFrom(service.getRecent('critical'));
    const req = http.expectOne((r) => r.url === IMAGING_RESULTS_URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('filter')).toBe('critical');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('100');
    req.flush(page([a], 0, 1));
    expect(await result).toEqual([a]);
  });

  it('getRecent reads every page and keeps rows without a patient', async () => {
    const a = row(IMAGING_RESULTS[0]);
    const b: Row = { ...IMAGING_RESULTS[1] };
    const result = firstValueFrom(service.getRecent('all'));
    http
      .expectOne((r) => r.url === IMAGING_RESULTS_URL && r.params.get('page') === '0')
      .flush(page([a], 0, 2));
    http
      .expectOne((r) => r.url === IMAGING_RESULTS_URL && r.params.get('page') === '1')
      .flush(page([b], 1, 2));
    expect(await result).toEqual([a, b]);
  });
});
