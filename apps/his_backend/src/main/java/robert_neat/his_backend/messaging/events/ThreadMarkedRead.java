package robert_neat.his_backend.messaging.events;

import java.util.UUID;

import robert_neat.his_backend.messaging.MessageThreadResponse;

/**
 * Zdarzenie domenowe: uzytkownik oznaczyl watek jako przeczytany. Nosi projekcje watku z `unreadCount: 0`
 * (viewer-scoped) do synchronizacji pozostalych sesji tego uzytkownika (`/user/queue/threads`, API.md, par. 10).
 * Konsument: `realtime/RealtimePublisher` (AFTER_COMMIT).
 */
public record ThreadMarkedRead(UUID staffId, MessageThreadResponse thread) {
}
