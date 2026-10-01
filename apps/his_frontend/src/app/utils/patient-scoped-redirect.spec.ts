import { afterEach, describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { PatientContextService } from '../services/patient-context.service';
import { patientScopedRedirect } from './patient-scoped-redirect';

describe('patientScopedRedirect', () => {
  // Storage must not leak into other specs (auth.service.spec asserts it is empty).
  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [],
    });
  });

  it('redirects to /select-patient with next query param when no patient is in context', () => {
    TestBed.runInInjectionContext(() => {
      const redirect = patientScopedRedirect('orders/lab/new');
      const result = redirect({} as never);
      expect(result).toBeInstanceOf(UrlTree);
      const router = TestBed.inject(Router);
      const url = router.serializeUrl(result as UrlTree);
      expect(url).toContain('/select-patient');
      expect(url).toContain('next=orders%2Flab%2Fnew');
    });
  });

  it('redirects to the nested patient route when a patient is in context', () => {
    TestBed.runInInjectionContext(() => {
      const ctx = TestBed.inject(PatientContextService);
      ctx.setPatient({
        id: 'pat-001',
        mrn: 'HIS/2026/000001',
        pesel: '68031437976',
        firstName: 'Jan',
        lastName: 'Kowalski',
        birthDate: '1968-03-14',
        gender: 'male',
        address: {
          street: 'A',
          buildingNumber: '1',
          postalCode: '00-001',
          city: 'Warszawa',
          country: 'Polska',
        },
        insurance: { status: 'active', nfzBranch: '07', payer: 'NFZ' },
        status: 'admitted',
        flags: [],
        createdAt: '2026-01-01T00:00:00.000Z',
        updatedAt: '2026-01-01T00:00:00.000Z',
      });

      const redirect = patientScopedRedirect('orders/lab/new');
      const result = redirect({} as never);
      expect(result).toBe('/patients/pat-001/orders/lab/new');
    });
  });
});
