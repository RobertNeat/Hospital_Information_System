package robert_neat.his_backend.patient;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Cialo `POST /patients/duplicate-check` (PESEL nie trafia do URL ani logow). */
public record DuplicateCheckRequest(
        @NotNull @Pattern(regexp = "\\d{11}", message = "PESEL musi miec dokladnie 11 cyfr") String pesel) {
}
