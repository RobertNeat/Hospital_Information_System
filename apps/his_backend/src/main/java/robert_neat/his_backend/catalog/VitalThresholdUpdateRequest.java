package robert_neat.his_backend.catalog;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cialo `PUT /vital-thresholds/{type}`. Kolejnosc `min &lt;= criticalLow &lt;= low &lt;= high &lt;= criticalHigh &lt;= max`
 * sprawdzana w {@link VitalThresholdService} (422, nie adnotacjami - zaleznosc miedzy polami).
 */
public record VitalThresholdUpdateRequest(
        @NotBlank @Size(max = 100) String label,
        @NotBlank @Size(max = 20) String unit,
        @NotNull BigDecimal low,
        @NotNull BigDecimal high,
        @NotNull BigDecimal criticalLow,
        @NotNull BigDecimal criticalHigh,
        @NotNull BigDecimal min,
        @NotNull BigDecimal max) {
}
