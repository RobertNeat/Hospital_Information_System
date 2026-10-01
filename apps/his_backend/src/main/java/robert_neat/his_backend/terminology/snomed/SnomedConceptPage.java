package robert_neat.his_backend.terminology.snomed;

import java.util.List;

/** Strona wynikow ValueSet/$expand. */
public record SnomedConceptPage(int total, int offset, List<SnomedConcept> concepts) {
}
