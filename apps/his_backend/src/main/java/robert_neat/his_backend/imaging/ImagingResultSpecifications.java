package robert_neat.his_backend.imaging;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import robert_neat.his_backend.lab.ResultAbnormalityFilter;

/**
 * Dynamiczne filtry wynikow: pacjent i nieprawidlowosc. Wynik obrazowy nie ma nasilenia zmian, a `critical` jest jego
 * jedyna flaga, wiec `abnormal` i `critical` zawezaja do wynikow z `critical = true` (jak w kontrakcie).
 */
final class ImagingResultSpecifications {

    private ImagingResultSpecifications() {
    }

    static Specification<ImagingResult> matching(UUID patientId, ResultAbnormalityFilter filter) {
        List<Specification<ImagingResult>> parts = new ArrayList<>();
        if (patientId != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("patientId"), patientId));
        }
        if (filter == ResultAbnormalityFilter.ABNORMAL || filter == ResultAbnormalityFilter.CRITICAL) {
            parts.add((root, query, cb) -> cb.isTrue(root.get("critical")));
        }
        return Specification.allOf(parts);
    }
}
