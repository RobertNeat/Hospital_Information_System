package robert_neat.his_backend.lab;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.order.Urgency;

/** Dynamiczne filtry worklisty zlecen - tylko warunki faktycznie podane. */
final class LabOrderSpecifications {

    private LabOrderSpecifications() {
    }

    static Specification<LabOrder> matching(UUID patientId, OrderStatus status, Urgency urgency, Instant orderedFrom,
            Instant orderedTo) {
        List<Specification<LabOrder>> parts = new ArrayList<>();
        if (patientId != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("patientId"), patientId));
        }
        if (status != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (urgency != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("urgency"), urgency));
        }
        if (orderedFrom != null) {
            parts.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("orderedAt"), orderedFrom));
        }
        if (orderedTo != null) {
            parts.add((root, query, cb) -> cb.lessThanOrEqualTo(root.<Instant>get("orderedAt"), orderedTo));
        }
        return Specification.allOf(parts);
    }
}
