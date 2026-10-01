package robert_neat.his_backend.terminology.snomed;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import robert_neat.his_backend.security.HisUserPrincipal;

@RestController
@RequestMapping("/api/v1/terminology/snomed")
public class TerminologyController {

    /** Domyslny zakres, gdy podano tylko `term`: SNOMED CT Concept i potomkowie. */
    static final String ALL_CONCEPTS_ECL = "<< 138875005";

    private final SnowstormClient client;
    private final TerminologySuggestionService suggestions;

    public TerminologyController(SnowstormClient client, TerminologySuggestionService suggestions) {
        this.client = client;
        this.suggestions = suggestions;
    }

    /** Podpowiedzi wg specjalizacji zalogowanego lekarza (ECL z konfiguracji `his.terminology.suggestions`). */
    @GetMapping("/suggestions")
    public SnomedConceptPage suggest(
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "20") int size) {
        TerminologyKind parsed = TerminologyKind.parse(kind);
        UUID staffId = principal instanceof HisUserPrincipal p ? p.staffId() : null;
        return suggestions.suggest(staffId, parsed, term, size);
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
