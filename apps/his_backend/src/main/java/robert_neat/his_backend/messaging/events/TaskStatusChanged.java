package robert_neat.his_backend.messaging.events;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.messaging.TaskStatus;

/** Zdarzenie domenowe: zmieniono status zadania (publikowane w transakcji, bez konsumenta). */
public record TaskStatusChanged(
        UUID taskId,
        UUID assignedToId,
        UUID createdById,
        TaskStatus previousStatus,
        TaskStatus status,
        UUID actorId,
        Instant at) {
}
