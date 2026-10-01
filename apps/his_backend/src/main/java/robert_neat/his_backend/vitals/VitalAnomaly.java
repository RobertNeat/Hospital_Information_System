package robert_neat.his_backend.vitals;

import java.math.BigDecimal;
import java.time.Instant;

import robert_neat.his_backend.catalog.VitalType;

/** `VitalAnomaly` z kontraktu (projekcja liczona z progow `vital_threshold`; nigdy nie jest zapisywana). */
public record VitalAnomaly(
        VitalType type,
        BigDecimal value,
        AnomalySeverity severity,
        AnomalyDirection direction,
        String message,
        Instant recordedAt) {
}
