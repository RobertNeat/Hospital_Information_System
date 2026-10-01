package robert_neat.his_backend.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** MessageThread z kontraktu; `unreadCount` jest liczony dla zalogowanego uzytkownika (`@viewerScoped`). */
public record MessageThreadResponse(
        UUID id,
        List<UUID> participantIds,
        String subject,
        UUID patientId,
        UUID createdById,
        Instant lastMessageAt,
        long unreadCount) {
}
