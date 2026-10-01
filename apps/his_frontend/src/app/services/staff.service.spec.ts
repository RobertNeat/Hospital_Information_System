import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { STAFF_URL, staffUrl } from '../config/api.config';
import type { StaffMember } from '../models';
import type { CurrentUser } from '../models/api';
import { AuthService } from './auth.service';
import { StaffService } from './staff.service';

const ANNA: StaffMember = {
  id: 's-1',
  title: 'lek.',
  firstName: 'Anna',
  lastName: 'Nowak',
  role: 'doctor',
  wardId: 'w-1',
  online: true,
};
const EWA: StaffMember = {
  id: 's-2',
  title: 'piel.',
  firstName: 'Ewa',
  lastName: 'Kowal',
  role: 'nurse',
  wardId: 'w-1',
  online: false,
};

describe('StaffService', () => {
  let service: StaffService;
  let http: HttpTestingController;
  let authenticated: boolean;
  const user = signal<CurrentUser | null>(null);

  beforeEach(() => {
    authenticated = false;
    user.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: { isAuthenticated: () => authenticated, currentUser: user },
        },
      ],
    });
    service = TestBed.inject(StaffService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('currentUser follows the signed-in AuthService user', () => {
    user.set({ ...ANNA, permissions: [] });
    expect(service.currentUser().lastName).toBe('Nowak');
    user.set({ ...EWA, permissions: [] });
    expect(service.currentUser().lastName).toBe('Kowal');
  });

  it('currentUser keeps the last user while the session is torn down', () => {
    user.set({ ...ANNA, permissions: [] });
    expect(service.currentUser().id).toBe('s-1');
    user.set(null);
    expect(service.currentUser().id).toBe('s-1');
  });

  it('currentUser throws before any sign-in', () => {
    expect(() => service.currentUser()).toThrow();
  });

  it('getStaff() without filters GETs once and reuses the cache', async () => {
    const first = firstValueFrom(service.getStaff());
    http.expectOne({ method: 'GET', url: STAFF_URL }).flush([ANNA, EWA]);
    expect(await first).toEqual([ANNA, EWA]);
    expect(await firstValueFrom(service.getStaff())).toEqual([ANNA, EWA]);
    http.expectNone(STAFF_URL);
  });

  it('getStaff(role, wardId) sends the filters as query params', async () => {
    const result = firstValueFrom(service.getStaff('doctor', 'w-1'));
    const req = http.expectOne((r) => r.url === STAFF_URL);
    expect(req.request.params.get('role')).toBe('doctor');
    expect(req.request.params.get('wardId')).toBe('w-1');
    req.flush([ANNA]);
    expect(await result).toEqual([ANNA]);
    expect(service.nameOf('s-1')).toBe('lek. Anna Nowak');
  });

  it('getById GETs one member', async () => {
    const result = firstValueFrom(service.getById('s-2'));
    http.expectOne({ method: 'GET', url: staffUrl('s-2') }).flush(EWA);
    expect((await result).lastName).toBe('Kowal');
  });

  it('getById propagates a 404', async () => {
    const result = firstValueFrom(service.getById('x'));
    http.expectOne(staffUrl('x')).flush(null, { status: 404, statusText: 'Not Found' });
    await expect(result).rejects.toBeTruthy();
  });

  it('nameOf formats "title firstName lastName" and falls back to the id', () => {
    service.load().subscribe();
    http.expectOne(STAFF_URL).flush([ANNA]);
    expect(service.nameOf('s-1')).toBe('lek. Anna Nowak');
    expect(service.nameOf('s-unknown')).toBe('s-unknown');
  });

  it('nameOf does not call the API when signed out', () => {
    expect(service.nameOf('s-1')).toBe('s-1');
    http.expectNone(STAFF_URL);
  });

  it('nameOf lazily loads once when signed in', () => {
    authenticated = true;
    expect(service.nameOf('s-2')).toBe('s-2');
    expect(service.nameOf('s-2')).toBe('s-2');
    http.expectOne(STAFF_URL).flush([ANNA, EWA]);
    expect(service.nameOf('s-2')).toBe('piel. Ewa Kowal');
  });

  it('nameOf does not retry after a failed lazy load', () => {
    authenticated = true;
    service.nameOf('s-1');
    http.expectOne(STAFF_URL).flush(null, { status: 403, statusText: 'Forbidden' });
    service.nameOf('s-1');
    http.expectNone(STAFF_URL);
  });

  it('clear() empties the cache so the next load refetches', () => {
    service.load().subscribe();
    http.expectOne(STAFF_URL).flush([ANNA]);
    service.clear();
    expect(service.staff()).toEqual([]);
    service.load().subscribe();
    http.expectOne(STAFF_URL).flush([ANNA]);
  });
});
