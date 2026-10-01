package robert_neat.his_backend.messaging.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import robert_neat.his_backend.messaging.Priority;

/**
 * Zdarzenie domenowe: wyslano wiadomosc (takze pierwsza przy zakladaniu watku; publikowane w transakcji, bez
 * konsumenta). `recipientIds` = uczestnicy watku poza nadawca (adresaci STOMP `/user/queue/messages`).
 */
public record MessageSent(
        UUID messageId,
        UUID threadId,
        String threadSubject,
        UUID patientId,
        UUID senderId,
        Priority priority,
        Instant sentAt,
        List<UUID> recipientIds) {
}
