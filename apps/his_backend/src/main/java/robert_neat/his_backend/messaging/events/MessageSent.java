package robert_neat.his_backend.messaging.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import robert_neat.his_backend.messaging.Priority;

/**
 * Zdarzenie domenowe: wyslano wiadomosc (takze pierwsza przy zakladaniu watku; publikowane w transakcji).
 * Konsumenci: `realtime/RealtimePublisher` (AFTER_COMMIT, push na zywo `/user/queue/messages` +
 * `/user/queue/threads`) i `alert/AlertEventListener` (synchronicznie, trwaly alert `system` bez adresatow/celu/
 * tresci watku - tylko nieadresowany wpis "byla nowa wiadomość" w historii alertow, bez duplikowania pushu na
 * zywo). `recipientIds` = uczestnicy watku poza nadawca (adresaci STOMP `/user/queue/messages`).
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
