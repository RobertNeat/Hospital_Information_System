package robert_neat.his_backend.ehr;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * AllergyCreateRequest z kontraktu. recordedById jest ignorowany (sesja); brak recordedAt = teraz,
 * brak status = active.
 */
public record AllergyCreateRequest(
        UUID patientId,
        @NotBlank @Size(max = 200) String substance,
        @NotNull AllergyCategory category,
        @NotBlank @Size(max = 500) String reaction,
        @NotNull AllergySeverity severity,
        AllergyStatus status,
        Instant recordedAt,
        UUID recordedById,
        @Size(max = 50) List<@NotBlank @Size(max = 10) String> atcCodes) {
}
