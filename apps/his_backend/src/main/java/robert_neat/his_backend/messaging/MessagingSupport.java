package robert_neat.his_backend.messaging;

import java.util.UUID;

import robert_neat.his_backend.common.api.ForbiddenException;
import robert_neat.his_backend.common.security.CurrentActor;

/** Wspolne drobiazgi serwisow modulu: aktor z tokenu i tolerancyjne parsowanie identyfikatorow. */
final class MessagingSupport {

    private MessagingSupport() {
    }

    static UUID actor(CurrentActor currentActor) {
        return currentActor.staffId()
                .orElseThrow(() -> new ForbiddenException("Brak powiazania sesji z pracownikiem"));
    }

    /** Identyfikator jest nieprzezroczysty dla klienta: niepoprawny format = "nie istnieje" (null -> 404). */
    static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
