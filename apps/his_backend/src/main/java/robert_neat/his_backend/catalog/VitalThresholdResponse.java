package robert_neat.his_backend.catalog;

import java.math.BigDecimal;

/** `VitalThreshold` z kontraktu (`min`/`max` to kolumny `min_value`/`max_value`). */
public record VitalThresholdResponse(
        VitalType type,
        String label,
        String unit,
        BigDecimal low,
        BigDecimal high,
        BigDecimal criticalLow,
        BigDecimal criticalHigh,
        BigDecimal min,
        BigDecimal max) {
}
