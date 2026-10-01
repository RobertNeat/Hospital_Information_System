package robert_neat.his_backend.messaging;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * TaskCreateRequest z kontraktu. `createdById` jest ignorowany (aktor z tokenu); `status` (opcjonalny) moze byc
 * wylacznie `open` - nowe zadanie zawsze zaczyna jako `open`.
 */
public record TaskCreateRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        UUID patientId,
        @NotNull UUID assignedToId,
        UUID createdById,
        Instant dueAt,
        @NotNull Priority priority,
        TaskStatus status) {
}
