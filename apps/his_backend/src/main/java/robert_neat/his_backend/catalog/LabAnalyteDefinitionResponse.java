package robert_neat.his_backend.catalog;

import java.math.BigDecimal;

/** `LabAnalyteDefinition` z kontraktu (`low`/`high` opcjonalne - pomijane, gdy brak zakresu). */
public record LabAnalyteDefinitionResponse(String code, String name, String unit, BigDecimal low, BigDecimal high) {
}
