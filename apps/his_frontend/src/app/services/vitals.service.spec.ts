import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import {
  VITALS_WARD_OVERVIEW_URL,
  VITAL_THRESHOLDS_URL,
  patientVitalsLatestUrl,
  patientVitalsUrl,
} from '../config/api.config';
import { VITALS } from '../mock-data/vitals.mock';
import { VITAL_THRESHOLDS } from '../mock-data/vital-thresholds.mock';
import type { VitalsRecordResponse, WardVitalsRow } from '../models';
import type { VitalSignsCreateRequest } from '../models/api';
import { VitalsService } from './vitals.service';

describe('VitalsService', () => {
  let service: VitalsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(VitalsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getVitals GETs the patient readings with the range', async () => {
    const result = firstValueFrom(service.getVitals('pat-001', '7d'));
    const req = http.expectOne((r) => r.url === patientVitalsUrl('pat-001'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('range')).toBe('7d');
    req.flush(VITALS.slice(0, 2));
    expect(await result).toHaveLength(2);
  });

  it('getLatest returns the reading, or undefined on 204 (no readings)', async () => {
    const found = firstValueFrom(service.getLatest('pat-001'));
    http.expectOne({ method: 'GET', url: patientVitalsLatestUrl('pat-001') }).flush(VITALS[0]);
    expect(await found).toEqual(VITALS[0]);

    const none = firstValueFrom(service.getLatest('pat-002'));
    http
      .expectOne({ method: 'GET', url: patientVitalsLatestUrl('pat-002') })
      .flush(null, { status: 204, statusText: 'No Content' });
    expect(await none).toBeUndefined();
  });

  it('addVitals POSTs the draft and returns the saved reading with server anomalies', async () => {
    const draft: VitalSignsCreateRequest = {
      patientId: 'pat-001',
      context: 'ward_round',
      heartRate: 150,
    };
    const response: VitalsRecordResponse = {
      saved: { ...VITALS[0], id: 'new', heartRate: 150 },
      anomalies: [
        {
          type: 'heartRate',
          value: 150,
          severity: 'critical',
          direction: 'high',
          message: 'Tętno: wartość krytycznie wysoka (150 /min).',
          recordedAt: VITALS[0].recordedAt,
        },
      ],
    };
    const result = firstValueFrom(service.addVitals(draft));
    const req = http.expectOne({ method: 'POST', url: patientVitalsUrl('pat-001') });
    expect(req.request.body).toEqual(draft);
    req.flush(response, { status: 201, statusText: 'Created' });
    expect(await result).toEqual(response);
  });

  it('getWardOverview GETs the overview with the optional ward filter', async () => {
    const rows: WardVitalsRow[] = [];
    const all = firstValueFrom(service.getWardOverview());
    const first = http.expectOne((r) => r.url === VITALS_WARD_OVERVIEW_URL);
    expect(first.request.params.has('wardId')).toBe(false);
    first.flush(rows);
    await all;

    const ward = firstValueFrom(service.getWardOverview('ward-int'));
    const second = http.expectOne((r) => r.url === VITALS_WARD_OVERVIEW_URL);
    expect(second.request.params.get('wardId')).toBe('ward-int');
    second.flush(rows);
    expect(await ward).toEqual(rows);
  });

  it('loadThresholds fetches once, caches and exposes thresholds by type', async () => {
    expect(service.thresholds()).toEqual({});
    const first = firstValueFrom(service.loadThresholds());
    http.expectOne({ method: 'GET', url: VITAL_THRESHOLDS_URL }).flush(VITAL_THRESHOLDS);
    await first;
    expect(service.thresholds().systolic?.criticalHigh).toBe(180);

    expect(await firstValueFrom(service.loadThresholds())).toEqual(VITAL_THRESHOLDS);
  });
});
