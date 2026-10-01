package robert_neat.his_backend.prescription;

import java.util.UUID;

/**
 * `DrugSafetyWarning` z kontraktu. `drugId` to lek sprawdzany (pozycja), ktorego dotyczy ostrzezenie; drugi lek
 * (interakcja, duplikat) jest nazwany w `message`.
 */
public record DrugSafetyWarningResponse(
        DrugSafetyWarningType type,
        DrugSafetySeverity severity,
        UUID drugId,
        String message) {
}
