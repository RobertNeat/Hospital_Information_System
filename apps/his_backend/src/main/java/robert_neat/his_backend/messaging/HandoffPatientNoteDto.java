package robert_neat.his_backend.messaging;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** HandoffPatientNote z kontraktu (SBAR) - ten sam ksztalt w zadaniu i odpowiedzi. */
public record HandoffPatientNoteDto(
        @NotNull UUID patientId,
        @NotBlank String situation,
        @NotBlank String background,
        @NotBlank String assessment,
        @NotBlank String recommendation) {
}
