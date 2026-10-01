package robert_neat.his_backend.catalog;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import robert_neat.his_backend.common.text.TextFolding;

/** Wyszukiwanie lekow: kazdy token musi pasowac do nazwy, substancji czynnej albo kodu ATC (podciag, bez diakrytykow). */
final class DrugSpecifications {

    private static final char ESCAPE = '\\';

    private DrugSpecifications() {
    }

    static Specification<Drug> matching(String term) {
        List<Specification<Drug>> parts = new ArrayList<>();
        if (term != null && !term.isBlank()) {
            for (String token : term.trim().split("\\s+")) {
                parts.add(tokenMatches(token));
            }
        }
        return Specification.allOf(parts);
    }

    private static Specification<Drug> tokenMatches(String token) {
        String pattern = "%" + escape(TextFolding.fold(token)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(folded(cb, root, "name"), pattern, ESCAPE),
                cb.like(folded(cb, root, "activeSubstance"), pattern, ESCAPE),
                cb.like(cb.lower(root.<String>get("atcCode")), pattern, ESCAPE));
    }

    private static Expression<String> folded(CriteriaBuilder cb, Root<Drug> root, String attribute) {
        return cb.lower(cb.function("translate", String.class, root.<String>get(attribute),
                cb.literal(TextFolding.SQL_FROM), cb.literal(TextFolding.SQL_TO)));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
