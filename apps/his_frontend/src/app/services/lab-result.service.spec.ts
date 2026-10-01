import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import {
  LAB_RESULTS_URL,
  labResultAcknowledgeUrl,
  labResultUrl,
  patientLabAnalytesUrl,
  patientLabResultsUrl,
  patientLabTrendUrl,
} from '../config/api.config';
import { LAB_RESULTS } from '../mock-data/lab-results.mock';
import type { AnalyteTrend, LabResult, PatientSummary, ResultWithPatient } from '../models';
import type { Page } from '../models/api';
import { LabResultService } from './lab-result.service';

type Row = ResultWithPatient<LabResult>;

const row = (result: LabResult): Row => ({ ...result, patient: {} as PatientSummary });

const page = (items: Row[], n: number, totalPages: number): Page<Row> => ({
  items,
  page: n,
  size: 100,
  totalElements: items.length,
  totalPages,
});

describe('LabResultService', () => {
  let service: LabResultService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LabResultService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getResults GETs the patient list without a filter by default', async () => {
    const result = firstValueFrom(service.getResults('pat-001'));
    const req = http.expectOne((r) => r.url === patientLabResultsUrl('pat-001'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([LAB_RESULTS[0]]);
    expect(await result).toEqual([LAB_RESULTS[0]]);
  });

  it('getResults sends the abnormality filter', () => {
    service.getResults('pat-001', 'critical').subscribe();
    const req = http.expectOne((r) => r.url === patientLabResultsUrl('pat-001'));
    expect(req.request.params.get('filter')).toBe('critical');
    req.flush([]);
  });

  it('getResultById GETs /lab-results/{id} and surfaces 404', async () => {
    const result = firstValueFrom(service.getResultById('lres-999'));
    http
      .expectOne({ method: 'GET', url: labResultUrl('lres-999') })
      .flush(null, { status: 404, statusText: 'Not Found' });
    await expect(result).rejects.toMatchObject({ status: 404 });
  });

  it('acknowledgeResult POSTs without a version and returns the reviewed result', async () => {
    const reviewed = { ...LAB_RESULTS[0], reviewedAt: '2026-01-01T00:00:00Z', reviewedById: 'u1' };
    const result = firstValueFrom(service.acknowledgeResult('lres-001'));
    const req = http.expectOne({ method: 'POST', url: labResultAcknowledgeUrl('lres-001') });
    expect(req.request.body).toEqual({});
    req.flush(reviewed);
    expect(await result).toEqual(reviewed);
  });

  it('getAnalyteTrend GETs the trend of one analyte', async () => {
    const trend: AnalyteTrend = {
      analyteCode: 'HGB',
      analyteName: 'Hemoglobina',
      unit: 'g/dL',
      points: [{ at: '2026-01-01T00:00:00Z', value: 13.2, flag: 'N' }],
    };
    const result = firstValueFrom(service.getAnalyteTrend('pat-001', 'HGB'));
    http.expectOne({ method: 'GET', url: patientLabTrendUrl('pat-001', 'HGB') }).flush(trend);
    expect(await result).toEqual(trend);
  });

  it('getTrendableAnalytes maps { code, name } to select options', async () => {
    const result = firstValueFrom(service.getTrendableAnalytes('pat-001'));
    http
      .expectOne({ method: 'GET', url: patientLabAnalytesUrl('pat-001') })
      .flush([{ code: 'HGB', name: 'Hemoglobina' }]);
    expect(await result).toEqual([{ value: 'HGB', label: 'Hemoglobina' }]);
  });

  it('getRecent sends filter and paging to the inbox endpoint', async () => {
    const a = row(LAB_RESULTS[0]);
    const result = firstValueFrom(service.getRecent('abnormal'));
    const req = http.expectOne((r) => r.url === LAB_RESULTS_URL);
    expect(req.request.params.get('filter')).toBe('abnormal');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('100');
    req.flush(page([a], 0, 1));
    expect(await result).toEqual([a]);
  });

  it('getRecent reads every page', async () => {
    const a = row(LAB_RESULTS[0]);
    const b = row(LAB_RESULTS[1]);
    const result = firstValueFrom(service.getRecent('all'));
    http
      .expectOne((r) => r.url === LAB_RESULTS_URL && r.params.get('page') === '0')
      .flush(page([a], 0, 2));
    http
      .expectOne((r) => r.url === LAB_RESULTS_URL && r.params.get('page') === '1')
      .flush(page([b], 1, 2));
    expect(await result).toEqual([a, b]);
  });
});
