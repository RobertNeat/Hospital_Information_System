import { effect, inject, signal, type Signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, of, Subject, switchMap } from 'rxjs';
import type { Coding, Diagnosis } from '../models';
import type { SnomedConcept } from '../models/api';
import { EhrService } from '../services/ehr.service';

export interface DiagnosisOption {
  label: string;
  value: string;
  coding: Coding;
}

/**
 * Patient's own diagnoses first (only `SNOMED`-coded ones - older ICD-10 entries cannot be resubmitted now that
 * the backend requires SCTID), then SNOMED CT suggestions for the doctor's specialty (deduplicated by code).
 */
export function buildDiagnosisOptions(
  diagnoses: Diagnosis[],
  suggestions: SnomedConcept[],
): DiagnosisOption[] {
  const seen = new Set<string>();
  const options: DiagnosisOption[] = [];
  for (const d of diagnoses) {
    if (d.code.system !== 'SNOMED' || seen.has(d.code.code)) continue;
    seen.add(d.code.code);
    options.push({
      label: `${d.code.code} — ${d.code.display}`,
      value: d.code.code,
      coding: d.code,
    });
  }
  for (const c of suggestions) {
    if (seen.has(c.code)) continue;
    seen.add(c.code);
    options.push({
      label: `${c.code} — ${c.display}`,
      value: c.code,
      coding: { system: 'SNOMED', code: c.code, display: c.display },
    });
  }
  return options;
}

/**
 * Debounced server-side search (`GET /terminology/snomed/suggestions?term=...`) over the diagnosis picker,
 * for rare diagnoses outside the specialty-scoped prefetch (~100 rows, no `term`). Injection context only
 * (uses `takeUntilDestroyed`). Feed `search()` from `p-autoComplete`'s `completeMethod`; an empty query
 * falls back to `baseOptions` (the prefetch merged with the patient's own diagnoses).
 */
export function injectDiagnosisSearch(
  baseOptions: Signal<DiagnosisOption[]>,
  patientDiagnoses: Signal<Diagnosis[]>,
): { options: Signal<DiagnosisOption[]>; search: (term: string) => void } {
  const ehrService = inject(EhrService);
  const options = signal<DiagnosisOption[]>([]);
  let searching = false;

  // Keep showing the (possibly still-loading) prefetch until the user actually searches.
  effect(() => {
    if (!searching) options.set(baseOptions());
  });

  const query$ = new Subject<string>();
  query$
    .pipe(
      debounceTime(300),
      switchMap((term) => {
        const trimmed = term.trim();
        if (!trimmed) return of(null);
        return ehrService
          .getSnomedSuggestions('diagnosis', trimmed)
          .pipe(catchError(() => of({ total: 0, offset: 0, concepts: [] })));
      }),
      takeUntilDestroyed(),
    )
    .subscribe((page) => {
      searching = page !== null;
      options.set(
        page === null ? baseOptions() : buildDiagnosisOptions(patientDiagnoses(), page.concepts),
      );
    });

  return {
    options,
    search: (term: string) => query$.next(term),
  };
}
