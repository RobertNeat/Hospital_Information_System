import { patientServiceStub } from '../testing/patient-service.stub';
import { ehrServiceStub } from '../testing/ehr-service.stub';
import { wardServiceStub } from '../testing/ward-service.stub';
import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { provideRouter, RedirectCommand, type ActivatedRouteSnapshot } from '@angular/router';
import { isObservable, firstValueFrom } from 'rxjs';
import { PatientContextService } from '../services/patient-context.service';
import { patientResolver } from './patient.resolver';

function routeWithParam(patientId: string | null): ActivatedRouteSnapshot {
  return {
    paramMap: { get: (key: string) => (key === 'patientId' ? patientId : null) },
  } as unknown as ActivatedRouteSnapshot;
}

describe('patientResolver', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        patientServiceStub,
        ehrServiceStub,
        wardServiceStub,
        MessageService,
        provideRouter([]),
      ],
    });
  });

  it('resolves the patient and sets PatientContextService', async () => {
    const result = TestBed.runInInjectionContext(() =>
      patientResolver(routeWithParam('pat-001'), {} as never),
    );
    const value = isObservable(result) ? await firstValueFrom(result) : await result;
    expect(value).not.toBeInstanceOf(RedirectCommand);

    const ctx = TestBed.inject(PatientContextService);
    expect(ctx.patientId()).toBe('pat-001');
  });

  it('redirects to /patients for an unknown patient id', async () => {
    const result = TestBed.runInInjectionContext(() =>
      patientResolver(routeWithParam('pat-999'), {} as never),
    );
    const value = isObservable(result) ? await firstValueFrom(result) : await result;
    expect(value).toBeInstanceOf(RedirectCommand);
  });

  it('redirects to /patients when there is no patientId param', async () => {
    const result = TestBed.runInInjectionContext(() =>
      patientResolver(routeWithParam(null), {} as never),
    );
    expect(result).toBeInstanceOf(RedirectCommand);
  });
});
