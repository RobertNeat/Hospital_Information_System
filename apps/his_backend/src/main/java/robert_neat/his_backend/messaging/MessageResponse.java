package robert_neat.his_backend.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Message z kontraktu; `readByIds` to projekcja z kursorow odczytu uczestnikow (nadawca zawsze, jako pierwszy). */
public record MessageResponse(
        UUID id,
        UUID threadId,
        UUID senderId,
        Instant sentAt,
        String body,
        Priority priority,
        List<UUID> readByIds) {
}
