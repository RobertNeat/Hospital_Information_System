import { Injectable, inject } from '@angular/core';
import { forkJoin, map } from 'rxjs';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { DRUGS } from '../mock-data/drugs.mock';
import type { Drug, DrugSafetyWarning, ID } from '../models';
import { mockError, mockResponse } from '../utils/mock-response';
import { EhrService } from './ehr.service';
import { PrescriptionService } from './prescription.service';

function normalize(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

@Injectable({ providedIn: 'root' })
export class DrugService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly ehrService = inject(EhrService);
  private readonly prescriptionService = inject(PrescriptionService);
  private readonly drugs: Drug[] = structuredClone(DRUGS);

  /** Matches trade name, active substance and ATC code. */
  search(term: string): Observable<Drug[]> {
    const t = normalize(term);
    const result = t
      ? this.drugs.filter(
          (d) =>
            normalize(d.name).includes(t) ||
            normalize(d.activeSubstance).includes(t) ||
            normalize(d.atcCode).includes(t),
        )
      : this.drugs;
    return mockResponse(result, this.latency);
  }

  getById(id: ID): Observable<Drug> {
    const found = this.drugs.find((d) => d.id === id);
    if (!found) return mockError(`Nie znaleziono leku o id ${id}`, this.latency);
    return mockResponse(found, this.latency);
  }

  /** Checks a drug against the patient's allergies, active prescriptions and max daily dose. */
  checkSafety(drug: Drug, patientId: ID): Observable<DrugSafetyWarning[]> {
    return forkJoin({
      allergies: this.ehrService.getAllergies(patientId),
      activeMedications: this.prescriptionService.getActiveMedications(patientId),
    }).pipe(
      map(({ allergies, activeMedications }) => {
        const warnings: DrugSafetyWarning[] = [];

        for (const allergy of allergies) {
          const substanceMatch = normalize(allergy.substance) === normalize(drug.activeSubstance);
          const atcMatch = (allergy.atcCodes ?? []).some((code) => drug.atcCode.startsWith(code));
          if (substanceMatch || atcMatch) {
            warnings.push({
              type: 'allergy',
              severity:
                allergy.severity === 'life_threatening' || allergy.severity === 'severe'
                  ? 'danger'
                  : 'warn',
              message: `Pacjent ma odnotowaną alergię na ${allergy.substance} (${allergy.reaction}).`,
            });
          }
        }

        const duplicateAtc = activeMedications.some(
          (item) => item.drugId !== drug.id && item.activeSubstance === drug.activeSubstance,
        );
        if (duplicateAtc) {
          warnings.push({
            type: 'duplicate',
            severity: 'warn',
            message: `Pacjent ma już przepisany lek zawierający ${drug.activeSubstance}.`,
          });
        }

        for (const item of activeMedications) {
          if ((drug.interactsWithAtc ?? []).length === 0) continue;
          const activeDrugAtc = this.drugs.find((d) => d.name === item.drugName)?.atcCode;
          if (
            activeDrugAtc &&
            drug.interactsWithAtc?.some((code) => activeDrugAtc.startsWith(code))
          ) {
            warnings.push({
              type: 'interaction',
              severity: 'warn',
              message: `Możliwa interakcja z aktualnie przyjmowanym lekiem ${item.drugName}.`,
            });
          }
        }

        return warnings;
      }),
    );
  }
}
