/** `TerminologyKind` z kontraktu (parametr `kind` podpowiedzi SNOMED CT). */
export type TerminologyKind = 'diagnosis' | 'symptom' | 'procedure';

/** Pojecie SNOMED CT (SCTID + nazwa wyswietlana); odpowiednik backendowego `SnomedConcept`. */
export interface SnomedConcept {
  code: string;
  display: string;
}

/** Strona wynikow `GET /terminology/snomed/suggestions` i `/concepts` (`SnomedConceptPage`). */
export interface SnomedConceptPage {
  total: number;
  offset: number;
  concepts: SnomedConcept[];
}
