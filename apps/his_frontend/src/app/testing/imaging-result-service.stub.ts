import { of, throwError } from 'rxjs';
import { IMAGING_RESULTS } from '../mock-data/imaging-results.mock';
import { PATIENTS } from '../mock-data/patients.mock';
import { WARDS } from '../mock-data/wards.mock';
import type { ImagingResult, ResultAbnormalityFilter } from '../models';
import { ImagingResultService } from '../services/imaging-result.service';
import { toPatientSummary } from '../utils/patient-summary';
import { TEST_USER } from './staff-service.stub';

// Imaging has a single abnormality flag: `abnormal` and `critical` both narrow to `critical`.
const matches = (r: ImagingResult, filter?: ResultAbnormalityFilter): boolean =>
  !filter || filter === 'all' || r.critical;

/** In-memory `ImagingResultService` double over the mock imaging results (fresh state per TestBed). */
export function createImagingResultServiceStub(): Partial<
  Record<keyof ImagingResultService, unknown>
> {
  const results: ImagingResult[] = structuredClone(IMAGING_RESULTS);
  const notFound = (id: string) => throwError(() => new Error(`Nie znaleziono wyniku ${id}`));

  return {
    getResults: (pid: string, filter?: ResultAbnormalityFilter) =>
      of(results.filter((r) => r.patientId === pid && matches(r, filter))),
    getResultById: (id: string) => {
      const found = results.find((r) => r.id === id);
      return found ? of(found) : notFound(id);
    },
    acknowledgeResult: (id: string) => {
      const index = results.findIndex((r) => r.id === id);
      if (index === -1) return notFound(id);
      results[index] = {
        ...results[index],
        reviewedAt: new Date().toISOString(),
        reviewedById: TEST_USER.id,
      };
      return of(results[index]);
    },
    getRecent: (filter: ResultAbnormalityFilter) =>
      of(
        results
          .filter((r) => matches(r, filter))
          .map((r) => {
            const patient = PATIENTS.find((p) => p.id === r.patientId);
            return { ...r, patient: patient ? toPatientSummary(patient, WARDS) : undefined };
          }),
      ),
  };
}

/** Test provider replacing the HTTP-backed `ImagingResultService` with the mock imaging results. */
export const imagingResultServiceStub = {
  provide: ImagingResultService,
  useFactory: createImagingResultServiceStub,
};
