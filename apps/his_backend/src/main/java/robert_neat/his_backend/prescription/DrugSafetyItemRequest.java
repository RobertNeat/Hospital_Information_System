package robert_neat.his_backend.prescription;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Pozycja sprawdzana pod katem bezpieczenstwa: lek i (opcjonalnie) dawkowanie, wymagane do kontroli `max_dose`. */
public record DrugSafetyItemRequest(
        @NotNull UUID drugId,
        @Valid DosageRequest dosage) {
}
