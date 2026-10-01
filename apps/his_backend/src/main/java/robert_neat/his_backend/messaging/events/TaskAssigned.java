package robert_neat.his_backend.messaging.events;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.messaging.Priority;

/**
 * Zdarzenie domenowe: utworzono zadanie przypisane do pracownika (publikowane w transakcji, bez konsumenta -
 * alert `task` dla `assignedToId` i push `/user/queue/tasks` w kolejnych etapach).
 */
public record TaskAssigned(
        UUID taskId,
        UUID assignedToId,
        UUID createdById,
        UUID patientId,
        String title,
        Priority priority,
        Instant dueAt,
        Instant at) {
}
