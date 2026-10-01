package robert_neat.his_backend.patient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import robert_neat.his_backend.common.text.TextFolding;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Dynamiczne filtry listy pacjentow (`term`, `status`, `wardId`) - tylko warunki faktycznie podane. */
final class PatientSpecifications {

    private static final char ESCAPE = '\\';

    private PatientSpecifications() {
    }

    static Specification<Patient> matching(String term, PatientStatus status, UUID wardId) {
        List<Specification<Patient>> parts = new ArrayList<>();
        if (term != null && !term.isBlank()) {
            for (String token : term.trim().split("\\s+")) {
                parts.add(tokenMatches(token));
            }
        }
        if (status != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (wardId != null) {
            parts.add(inWard(wardId));
        }
        return Specification.allOf(parts);
    }

    /** Kazdy token musi pasowac do nazwiska, imienia, MRN albo PESEL (podciag, bez wielkosci liter i diakrytykow). */
    private static Specification<Patient> tokenMatches(String token) {
        String pattern = "%" + escape(TextFolding.fold(token)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(folded(cb, root, "lastName"), pattern, ESCAPE),
                cb.like(folded(cb, root, "firstName"), pattern, ESCAPE),
                cb.like(folded(cb, root, "mrn"), pattern, ESCAPE),
                cb.like(root.<String>get("pesel"), pattern, ESCAPE));
    }

    private static Expression<String> folded(CriteriaBuilder cb, Root<Patient> root, String attribute) {
        return cb.lower(cb.function("translate", String.class, root.<String>get(attribute),
                cb.literal(TextFolding.SQL_FROM), cb.literal(TextFolding.SQL_TO)));
    }

    /** Pacjent `admitted` z aktywnym przyjeciem na danym oddziale. */
    private static Specification<Patient> inWard(UUID wardId) {
        return (root, query, cb) -> {
            Subquery<UUID> active = query.subquery(UUID.class);
            Root<Admission> admission = active.from(Admission.class);
            active.select(admission.<UUID>get("id")).where(
                    cb.equal(admission.get("patientId"), root.get("id")),
                    cb.equal(admission.get("status"), AdmissionRecordStatus.ACTIVE),
                    cb.equal(admission.get("wardId"), wardId));
            Predicate admitted = cb.equal(root.get("status"), PatientStatus.ADMITTED);
            return cb.and(admitted, cb.exists(active));
        };
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
