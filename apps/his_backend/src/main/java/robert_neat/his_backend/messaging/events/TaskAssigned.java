package robert_neat.his_backend.messaging.events;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.messaging.Priority;

/**
 * Zdarzenie domenowe: utworzono zadanie przypisane do pracownika (publikowane w transakcji). Konsumenci:
 * `alert/AlertEventListener` (synchronicznie, alert `task` dla `assignedToId`) i `realtime/RealtimePublisher`
 * (AFTER_COMMIT, push `/user/queue/tasks`).
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
