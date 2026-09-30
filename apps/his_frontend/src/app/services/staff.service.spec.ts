import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { StaffService } from './staff.service';

describe('StaffService', () => {
  let service: StaffService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    service = TestBed.inject(StaffService);
  });

  it('currentUser is stf-001 (lek. Anna Nowak)', () => {
    expect(service.currentUser().id).toBe('stf-001');
    expect(service.currentUser().lastName).toBe('Nowak');
  });

  it('returns all staff when no role filter is given', async () => {
    const staff = await firstValueFrom(service.getStaff());
    expect(staff.length).toBeGreaterThanOrEqual(10);
  });

  it('filters staff by role', async () => {
    const doctors = await firstValueFrom(service.getStaff('doctor'));
    expect(doctors.every((s) => s.role === 'doctor')).toBe(true);
    const nurses = await firstValueFrom(service.getStaff('nurse'));
    expect(nurses.every((s) => s.role === 'nurse')).toBe(true);
  });

  it('getById returns the matching staff member', async () => {
    const staff = await firstValueFrom(service.getById('stf-002'));
    expect(staff.lastName).toBe('Wiśniewski');
  });

  it('getById errors for an unknown id', async () => {
    await expect(firstValueFrom(service.getById('stf-999'))).rejects.toThrow();
  });

  it('nameOf formats "title firstName lastName"', () => {
    expect(service.nameOf('stf-001')).toBe('lek. Anna Nowak');
  });

  it('seed staff have employeeId, 7-digit pwz and active account', async () => {
    const staff = await firstValueFrom(service.getStaff());
    expect(staff.every((s) => s.employeeId && /^\d{7}$/.test(s.pwz ?? ''))).toBe(true);
    expect(staff.every((s) => s.accountStatus === 'active')).toBe(true);
  });
});
