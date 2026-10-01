package robert_neat.his_backend.catalog;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Progi parametrow zyciowych (`GET /vital-thresholds`), tylko odczyt. */
@Service
@Transactional(readOnly = true)
public class VitalThresholdService {

    private final VitalThresholdRepository thresholds;

    VitalThresholdService(VitalThresholdRepository thresholds) {
        this.thresholds = thresholds;
    }

    /** Progi wszystkich parametrow w kolejnosci `VitalType`. */
    public List<VitalThresholdResponse> list() {
        return thresholds.findAll().stream()
                .sorted(Comparator.comparing(VitalThreshold::getType))
                .map(CatalogMapper::toResponse).toList();
    }
}
