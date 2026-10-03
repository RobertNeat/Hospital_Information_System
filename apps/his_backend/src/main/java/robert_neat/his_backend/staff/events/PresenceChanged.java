package robert_neat.his_backend.staff.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Zdarzenie domenowe: zmiana obecnosci pracownika (publikowane poza transakcja, przy przejsciu
 * offline -> online lub online -> offline w {@code staff/PresenceRegistry}). Konsument:
 * `realtime/RealtimePublisher` (push `/topic/presence`).
 */
public record PresenceChanged(UUID staffId, boolean online, Instant at) {
}
