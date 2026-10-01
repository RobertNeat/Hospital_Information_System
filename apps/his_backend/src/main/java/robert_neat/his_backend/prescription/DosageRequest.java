package robert_neat.his_backend.prescription;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import robert_neat.his_backend.catalog.AdministrationRoute;

/** `DosageInstruction` z kontraktu (zadanie). `dose` i `durationDays` > 0; `dose` max 3 miejsca po przecinku. */
public record DosageRequest(
        @NotNull @Positive @Digits(integer = 7, fraction = 3) BigDecimal dose,
        @NotBlank @Size(max = 30) String doseUnit,
        @NotNull AdministrationRoute route,
        @NotNull DoseFrequency frequency,
        List<@NotNull TimeOfDay> timesOfDay,
        @NotNull @Positive Integer durationDays,
        @NotNull Boolean asNeeded,
        @Positive Integer maxPerDay,
        String instructions) {
}
