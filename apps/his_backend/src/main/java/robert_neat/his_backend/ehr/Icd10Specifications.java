package robert_neat.his_backend.ehr;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import robert_neat.his_backend.common.text.TextFolding;

/** Wyszukiwanie w slowniku ICD-10: kazdy token musi pasowac do kodu albo nazwy (podciag, bez diakrytykow). */
final class Icd10Specifications {

    private static final char ESCAPE = '\\';

    private Icd10Specifications() {
    }

    static Specification<Icd10Code> matching(String term) {
        List<Specification<Icd10Code>> parts = new ArrayList<>();
        if (term != null && !term.isBlank()) {
            for (String token : term.trim().split("\\s+")) {
                parts.add(tokenMatches(token));
            }
        }
        return Specification.allOf(parts);
    }

    private static Specification<Icd10Code> tokenMatches(String token) {
        String pattern = "%" + escape(TextFolding.fold(token)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.<String>get("code")), pattern, ESCAPE),
                cb.like(foldedDisplay(cb, root), pattern, ESCAPE));
    }

    private static Expression<String> foldedDisplay(CriteriaBuilder cb, Root<Icd10Code> root) {
        return cb.lower(cb.function("translate", String.class, root.<String>get("display"),
                cb.literal(TextFolding.SQL_FROM), cb.literal(TextFolding.SQL_TO)));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
