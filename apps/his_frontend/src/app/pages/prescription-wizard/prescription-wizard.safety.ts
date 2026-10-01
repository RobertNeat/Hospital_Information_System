import { computed, effect, inject, signal, type Signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder } from '@angular/forms';
import { catchError, map, of, switchMap, tap } from 'rxjs';
import type { Drug, DrugSafetyWarning, PrescriptionItem } from '../../models';
import { DrugService } from '../../services/drug.service';
import { tryAdvance } from '../../utils/wizard';

/**
 * Step 1 drug safety check + allergy-override gate. Injection context only. The backend checks
 * the whole working prescription (already added `items` + the selected drug as the last
 * position); only warnings about the selected drug are shown and gate the step.
 */
export function createDrugSafetyState(
  selectedDrug: Signal<Drug | null>,
  patientId: Signal<string>,
  items: Signal<PrescriptionItem[]>,
) {
  const fb = inject(FormBuilder).nonNullable;
  const drugService = inject(DrugService);

  const form = fb.group({
    overrideConfirmed: fb.control(false),
    overrideJustification: fb.control(''),
  });

  const checkFailed = signal(false);

  // Re-runs the check through switchMap whenever the selected drug changes, so a fast
  // drug swap cannot leave stale warnings from the previous drug on screen.
  const warnings = toSignal(
    toObservable(selectedDrug).pipe(
      switchMap((drug) => {
        if (!drug) {
          checkFailed.set(false);
          return of<DrugSafetyWarning[]>([]);
        }
        const request = [
          ...items().map(({ drugId, dosage }) => ({ drugId, dosage })),
          { drugId: drug.id },
        ];
        return drugService.checkSafety(patientId(), request).pipe(
          map((all) => all.filter((w) => !w.drugId || w.drugId === drug.id)),
          tap(() => checkFailed.set(false)),
          // Advisory check: a failure (e.g. 403 for non-doctors) must be visible, not silent.
          catchError(() => {
            checkFailed.set(true);
            return of<DrugSafetyWarning[]>([]);
          }),
        );
      }),
    ),
    { initialValue: [] as DrugSafetyWarning[] },
  );
  const loading = signal(false);

  // `warnings` recomputing (including to `[]` when the drug is cleared) means the
  // in-flight check has resolved.
  effect(() => {
    warnings();
    loading.set(false);
  });

  const hasAllergyDanger = computed(() =>
    warnings().some((w) => w.type === 'allergy' && w.severity === 'danger'),
  );

  const resetOverride = (): void =>
    form.reset({ overrideConfirmed: false, overrideJustification: '' });

  /** Gate for "Dalej" on step 1 once a drug is selected. */
  const advance = (activate: (v: number) => void): void => {
    if (loading()) {
      // Block "Dalej" while the safety check is in flight so a fast click cannot skip
      // the allergy-override gate (ADDENDUM-inspired race fix).
      return;
    }
    if (hasAllergyDanger()) {
      const ok = tryAdvance(form, activate, 2);
      if (ok && !form.value.overrideConfirmed) {
        form.controls.overrideConfirmed.setErrors({ required: true });
      }
      if (
        form.value.overrideConfirmed &&
        (form.value.overrideJustification ?? '').trim().length === 0
      ) {
        form.controls.overrideJustification.setErrors({ required: true });
        form.markAllAsTouched();
        return;
      }
      if (!form.value.overrideConfirmed) return;
    }
    activate(2);
  };

  return { form, warnings, loading, checkFailed, hasAllergyDanger, resetOverride, advance };
}
