package robert_neat.his_backend.terminology.snomed;

/** Referencja do pojecia SNOMED CT (SCTID + nazwa wyswietlana). */
public record SnomedConcept(String code, String display) {
}
