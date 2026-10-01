package robert_neat.his_backend.prescription;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import robert_neat.his_backend.catalog.ReimbursementLevel;

/**
 * `PrescriptionItem` w zadaniu wystawienia. Pola snapshotu (`drugName`, `activeSubstance`, `strength`, `form`) oraz
 * `id` kopiuje/nadaje backend - jesli klient je przesle, sa ignorowane (nieznane pola nie powoduja bledu).
 */
public record PrescriptionItemRequest(
        @NotNull UUID drugId,
        @NotNull @Valid DosageRequest dosage,
        @NotNull @Positive Integer quantityPackages,
        @NotNull ReimbursementLevel reimbursement,
        @NotNull Boolean substitutionAllowed) {
}
