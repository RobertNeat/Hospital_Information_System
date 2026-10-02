import type { Coding, Diagnosis } from '../models';
import type { SnomedConcept } from '../models/api';

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
