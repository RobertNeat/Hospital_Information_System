import { of, throwError } from 'rxjs';
import { ALLERGIES } from '../mock-data/allergies.mock';
import { DRUGS } from '../mock-data/drugs.mock';
import type { DrugSafetyWarning } from '../models';
import type { DrugSafetyItemRequest } from '../models/api';
import { DrugService } from '../services/drug.service';

const fold = (s: string): string => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();

/** In-memory `DrugService` double over the mock catalog; the safety check covers allergies only. */
export function createDrugServiceStub(): Partial<Record<keyof DrugService, unknown>> {
  return {
    search: (term: string) => {
      const t = fold(term.trim());
      return of(
        t
          ? DRUGS.filter((d) =>
              [d.name, d.activeSubstance, d.atcCode].some((v) => fold(v).includes(t)),
            )
          : DRUGS,
      );
    },
    getById: (id: string) => {
      const drug = DRUGS.find((d) => d.id === id);
      return drug ? of(drug) : throwError(() => new Error(`Nie znaleziono leku ${id}`));
    },
    checkSafety: (patientId: string, items: DrugSafetyItemRequest[]) => {
      const warnings: DrugSafetyWarning[] = [];
      for (const item of items) {
        const drug = DRUGS.find((d) => d.id === item.drugId);
        if (!drug) continue;
        for (const allergy of ALLERGIES.filter(
          (a) => a.patientId === patientId && a.status === 'active',
        )) {
          const match =
            fold(allergy.substance) === fold(drug.activeSubstance) ||
            (allergy.atcCodes ?? []).some((code) => drug.atcCode.startsWith(code));
          if (match) {
            warnings.push({
              type: 'allergy',
              drugId: drug.id,
              severity:
                allergy.severity === 'life_threatening' || allergy.severity === 'severe'
                  ? 'danger'
                  : 'warn',
              message: `Alergia: ${allergy.substance}`,
            });
          }
        }
      }
      return of(warnings);
    },
  };
}

/** Test provider replacing the HTTP-backed `DrugService` with the mock catalog. */
export const drugServiceStub = {
  provide: DrugService,
  useFactory: createDrugServiceStub,
};
