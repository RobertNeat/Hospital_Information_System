package robert_neat.his_backend.terminology.snomed;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/terminology/snomed")
public class TerminologyController {

    /** Domyslny zakres, gdy podano tylko `term`: SNOMED CT Concept i potomkowie. */
    static final String ALL_CONCEPTS_ECL = "<< 138875005";

    private final SnowstormClient client;

    public TerminologyController(SnowstormClient client) {
        this.client = client;
    }

    @GetMapping("/concepts")
    public SnomedConceptPage search(
            @RequestParam(required = false) String ecl,
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        boolean hasEcl = ecl != null && !ecl.isBlank();
        boolean hasTerm = term != null && !term.isBlank();
        if (!hasEcl && !hasTerm) {
            throw new TerminologyException.InvalidRequest("Wymagany jest parametr ecl lub term", null);
        }
        return client.expandEcl(hasEcl ? ecl : ALL_CONCEPTS_ECL, hasTerm ? term : null, limit, offset);
    }

    @GetMapping("/concepts/{sctid}")
    public SnomedConcept get(@PathVariable String sctid) {
        return client.lookup(sctid);
    }
}
