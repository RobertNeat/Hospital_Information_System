package robert_neat.his_backend.messaging;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * ThreadCreateRequest z kontraktu. Tworca (z tokenu) jest dolaczany jako uczestnik niezaleznie od
 * `participantIds`; powtorzenia sa scalane.
 */
public record ThreadCreateRequest(
        @NotNull List<@NotNull UUID> participantIds,
        @NotBlank @Size(max = 200) String subject,
        UUID patientId,
        @NotNull @Valid FirstMessage firstMessage) {

    public record FirstMessage(@NotBlank String body, @NotNull Priority priority) {
    }
}
