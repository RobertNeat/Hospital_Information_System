package robert_neat.his_backend.prescription;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamiczne filtry listy recept - tylko warunki faktycznie podane. Filtr `status` dziala na statusie EFEKTYWNYM
 * (patrz {@link Prescription#effectiveStatus}): `expired` obejmuje tez "zywe" recepty po terminie, a `issued` /
 * `partially_dispensed` - tylko te, ktorych termin nie minal.
 */
final class PrescriptionSpecifications {

    private PrescriptionSpecifications() {
    }

    static Specification<Prescription> matching(UUID patientId, UUID prescriberId, PrescriptionStatus status,
            PrescriptionKind kind, LocalDate today) {
        List<Specification<Prescription>> parts = new ArrayList<>();
        if (patientId != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("patientId"), patientId));
        }
        if (prescriberId != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("prescriberId"), prescriberId));
        }
        if (kind != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("kind"), kind));
        }
        if (status != null) {
            parts.add(effectiveStatus(status, today));
        }
        return Specification.allOf(parts);
    }

    private static Specification<Prescription> effectiveStatus(PrescriptionStatus status, LocalDate today) {
        return (root, query, cb) -> {
            var stored = root.<PrescriptionStatus>get("status");
            var validUntil = root.<LocalDate>get("validUntil");
            if (status == PrescriptionStatus.EXPIRED) {
                return cb.or(cb.equal(stored, PrescriptionStatus.EXPIRED),
                        cb.and(stored.in(PrescriptionStatus.ISSUED, PrescriptionStatus.PARTIALLY_DISPENSED),
                                cb.lessThan(validUntil, today)));
            }
            if (status.isOpen()) {
                return cb.and(cb.equal(stored, status), cb.greaterThanOrEqualTo(validUntil, today));
            }
            return cb.equal(stored, status);
        };
    }
}
