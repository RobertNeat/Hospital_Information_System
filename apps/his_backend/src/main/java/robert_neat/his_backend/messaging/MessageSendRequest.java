package robert_neat.his_backend.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** MessageSendRequest z kontraktu; nadawca z tokenu. */
public record MessageSendRequest(@NotBlank String body, @NotNull Priority priority) {
}
