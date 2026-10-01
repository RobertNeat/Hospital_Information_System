package robert_neat.his_backend.catalog;

import java.math.BigDecimal;

/** `DoseQuantity` z kontraktu. */
public record DoseQuantityResponse(BigDecimal value, String unit) {
}
