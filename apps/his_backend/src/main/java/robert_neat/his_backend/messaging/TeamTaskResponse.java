package robert_neat.his_backend.messaging;

import java.time.Instant;
import java.util.UUID;

/** TeamTask z kontraktu (pola opcjonalne pomijane, gdy brak) + audyt i `version`. */
public record TeamTaskResponse(
        UUID id,
        String title,
        String description,
        UUID patientId,
        UUID assignedToId,
        UUID createdById,
        Instant createdAt,
        Instant dueAt,
        Priority priority,
        TaskStatus status,
        Instant updatedAt,
        UUID updatedById,
        long version) {
}
