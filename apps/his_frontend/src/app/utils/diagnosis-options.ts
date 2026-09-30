import type { Coding, Diagnosis } from '../models';

export interface DiagnosisOption {
  label: string;
  value: string;
  coding: Coding;
}

/** Patient's own diagnoses first, then the remaining ICD-10 dictionary entries (deduplicated by code). */
export function buildDiagnosisOptions(diagnoses: Diagnosis[], icd10: Coding[]): DiagnosisOption[] {
  const seen = new Set<string>();
  const options: DiagnosisOption[] = [];
  for (const d of diagnoses) {
    if (seen.has(d.code.code)) continue;
    seen.add(d.code.code);
    options.push({
      label: `${d.code.code} — ${d.code.display}`,
      value: d.code.code,
      coding: d.code,
    });
  }
  for (const c of icd10) {
    if (seen.has(c.code)) continue;
    seen.add(c.code);
    options.push({ label: `${c.code} — ${c.display}`, value: c.code, coding: c });
  }
  return options;
}
