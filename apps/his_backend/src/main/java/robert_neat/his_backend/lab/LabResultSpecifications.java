package robert_neat.his_backend.lab;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Dynamiczne filtry wynikow: pacjent i nieprawidlowosc (istnienie obserwacji z odpowiednia flaga). */
final class LabResultSpecifications {

    private LabResultSpecifications() {
    }

    static Specification<LabResult> matching(UUID patientId, ResultAbnormalityFilter filter) {
        List<Specification<LabResult>> parts = new ArrayList<>();
        if (patientId != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("patientId"), patientId));
        }
        if (filter == ResultAbnormalityFilter.ABNORMAL) {
            parts.add(hasObservationWithFlag(EnumSet.complementOf(EnumSet.of(ObservationFlag.N))));
        } else if (filter == ResultAbnormalityFilter.CRITICAL) {
            parts.add(hasObservationWithFlag(EnumSet.of(ObservationFlag.LL, ObservationFlag.HH)));
        }
        return Specification.allOf(parts);
    }

    private static Specification<LabResult> hasObservationWithFlag(EnumSet<ObservationFlag> flags) {
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            Root<LabResult> correlated = sub.correlate(root);
            Join<LabResult, LabObservation> observation = correlated.join("observations");
            sub.select(observation.<UUID>get("id")).where(observation.get("flag").in(flags));
            return cb.exists(sub);
        };
    }
}
