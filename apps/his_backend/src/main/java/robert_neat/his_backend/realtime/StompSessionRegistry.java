package robert_neat.his_backend.realtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import robert_neat.his_backend.security.HisUserPrincipal;

/**
 * Sesje STOMP uwierzytelnione po CONNECT, z identyfikatorem konta i wersja tokenu z chwili polaczenia -
 * dane wejsciowe dla {@link StompTokenVersionSweeper} (re-check poza CONNECT, patrz tamtejszy javadoc).
 * Wpis jest usuwany przy DISCONNECT ({@link PresenceEventListener} obsluguje ten sam cykl zycia sesji).
 */
@Component
class StompSessionRegistry {

    /** Konto i wersja tokenu zarejestrowane przy CONNECT danej sesji. */
    record SessionAccount(UUID accountId, int tokenVersion) {
    }

    private final Map<String, SessionAccount> bySessionId = new ConcurrentHashMap<>();

    void register(String sessionId, HisUserPrincipal principal, int tokenVersion) {
        bySessionId.put(sessionId, new SessionAccount(principal.accountId(), tokenVersion));
    }

    void remove(String sessionId) {
        bySessionId.remove(sessionId);
    }

    /** Migawka do porownania wsadowego z baza (bezpieczna do iteracji poza synchronizacja). */
    Map<String, SessionAccount> snapshot() {
        return Map.copyOf(bySessionId);
    }
}
