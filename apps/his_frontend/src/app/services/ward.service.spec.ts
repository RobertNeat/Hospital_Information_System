import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { WARDS_URL } from '../config/api.config';
import type { Ward } from '../models';
import { AuthService } from './auth.service';
import { WardService } from './ward.service';

const SOR: Ward = {
  id: 'w-1',
  name: 'Szpitalny Oddział Ratunkowy',
  shortName: 'SOR',
  floor: '0',
  beds: 10,
};
const INT: Ward = { id: 'w-2', name: 'Interna', shortName: 'INT', floor: '2', beds: 20 };

describe('WardService', () => {
  let service: WardService;
  let http: HttpTestingController;
  let authenticated: boolean;

  beforeEach(() => {
    authenticated = false;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { isAuthenticated: () => authenticated } },
      ],
    });
    service = TestBed.inject(WardService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GETs wards, fills the cache and reuses it', async () => {
    const first = firstValueFrom(service.getWards());
    http.expectOne({ method: 'GET', url: WARDS_URL }).flush([SOR, INT]);
    expect(await first).toEqual([SOR, INT]);

    expect(await firstValueFrom(service.getWards())).toEqual([SOR, INT]);
    http.expectNone(WARDS_URL);
  });

  it('shares one in-flight request between concurrent callers', () => {
    service.load().subscribe();
    service.load().subscribe();
    http.expectOne(WARDS_URL).flush([SOR]);
  });

  it('load(true) refetches', () => {
    service.load().subscribe();
    http.expectOne(WARDS_URL).flush([SOR]);
    service.load(true).subscribe();
    http.expectOne(WARDS_URL).flush([SOR, INT]);
    expect(service.wards()).toHaveLength(2);
  });

  it('nameOf resolves a loaded ward id synchronously', () => {
    service.load().subscribe();
    http.expectOne(WARDS_URL).flush([SOR, INT]);
    expect(service.nameOf('w-1')).toContain('Ratunkowy');
  });

  it('nameOf falls back to the id for an unknown ward', () => {
    service.load().subscribe();
    http.expectOne(WARDS_URL).flush([SOR]);
    expect(service.nameOf('w-unknown')).toBe('w-unknown');
  });

  it('nameOf does not call the API when signed out', () => {
    expect(service.nameOf('w-1')).toBe('w-1');
    http.expectNone(WARDS_URL);
  });

  it('nameOf lazily loads the cache once when signed in', () => {
    authenticated = true;
    expect(service.nameOf('w-2')).toBe('w-2');
    expect(service.nameOf('w-2')).toBe('w-2');
    http.expectOne(WARDS_URL).flush([SOR, INT]);
    expect(service.nameOf('w-2')).toBe('Interna');
  });

  it('nameOf does not retry after a failed lazy load', () => {
    authenticated = true;
    service.nameOf('w-1');
    http.expectOne(WARDS_URL).flush(null, { status: 403, statusText: 'Forbidden' });
    service.nameOf('w-1');
    http.expectNone(WARDS_URL);
  });

  it('clear() empties the cache so the next load refetches', () => {
    service.load().subscribe();
    http.expectOne(WARDS_URL).flush([SOR]);
    service.clear();
    expect(service.wards()).toEqual([]);
    service.load().subscribe();
    http.expectOne(WARDS_URL).flush([SOR]);
  });

  it('nameOf returns an empty string for an absent id without calling the API', () => {
    authenticated = true;
    expect(service.nameOf(undefined)).toBe('');
    http.expectNone(WARDS_URL);
  });
});
