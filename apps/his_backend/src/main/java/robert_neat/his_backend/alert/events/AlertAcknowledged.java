package robert_neat.his_backend.alert.events;

import java.util.UUID;

import robert_neat.his_backend.alert.AlertResponse;

/**
 * Zdarzenie domenowe: uzytkownik potwierdzil alert. Nosi projekcje alertu z jego stanem potwierdzenia
 * (viewer-scoped) do synchronizacji sesji tego uzytkownika (`/user/queue/alerts`, API.md, par. 10).
 * Konsument: `realtime/RealtimePublisher` (AFTER_COMMIT).
 */
public record AlertAcknowledged(UUID staffId, AlertResponse alert) {
}
