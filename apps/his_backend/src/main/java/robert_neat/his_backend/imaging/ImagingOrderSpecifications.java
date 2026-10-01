package robert_neat.his_backend.imaging;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import robert_neat.his_backend.catalog.ImagingModality;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.order.Urgency;

/** Dynamiczne filtry worklisty zlecen obrazowych - tylko warunki faktycznie podane. */
final class ImagingOrderSpecifications {

    private ImagingOrderSpecifications() {
    }

    static Specification<ImagingOrder> matching(UUID patientId, OrderStatus status, Urgency urgency,
            ImagingModality modality) {
        List<Specification<ImagingOrder>> parts = new ArrayList<>();
        if (patientId != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("patientId"), patientId));
        }
        if (status != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (urgency != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("urgency"), urgency));
        }
        if (modality != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("modality"), modality));
        }
        return Specification.allOf(parts);
    }
}
