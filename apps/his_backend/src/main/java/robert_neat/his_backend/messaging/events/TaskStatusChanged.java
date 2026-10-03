package robert_neat.his_backend.messaging.events;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.messaging.TaskStatus;

/**
 * Zdarzenie domenowe: zmieniono status zadania (publikowane w transakcji). Konsumenci: `realtime/RealtimePublisher`
 * (AFTER_COMMIT, push `/user/queue/tasks`) i `alert/AlertEventListener` (synchronicznie, trwaly alert `task` tylko
 * dla stanow koncowych `done`/`cancelled`, adresat = druga strona niz aktor zmiany).
 */
public record TaskStatusChanged(
        UUID taskId,
        UUID assignedToId,
        UUID createdById,
        TaskStatus previousStatus,
        TaskStatus status,
        UUID actorId,
        Instant at) {
}
