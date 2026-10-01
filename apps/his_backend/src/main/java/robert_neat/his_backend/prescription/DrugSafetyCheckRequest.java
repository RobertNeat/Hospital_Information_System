package robert_neat.his_backend.prescription;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * `DrugSafetyCheckRequest` z kontraktu (`patientId`, `drugId`, `dosage?`) rozszerzone o `items` - cala robocza
 * recepta naraz (kontrola duplikatow i interakcji MIEDZY pozycjami). Wymagany `drugId` albo niepusta `items`;
 * podane razem tworza jedna liste (`drugId` jako pierwsza pozycja).
 */
public record DrugSafetyCheckRequest(
        @NotNull UUID patientId,
        UUID drugId,
        @Valid DosageRequest dosage,
        List<@NotNull @Valid DrugSafetyItemRequest> items) {
}
