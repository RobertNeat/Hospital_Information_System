package robert_neat.his_backend.security;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Odczyt biezacej wersji tokenu konta (dla sprawdzenia claimu `tv` przy kazdym zadaniu/ramce STOMP).
 * Interfejs zyje w `security`, zeby ten pakiet (w tym {@link SecurityConfig}) nie zalezal bezposrednio
 * od JPA/`staff`; implementacja ({@code staff.UserAccountRepository}) jest wstrzykiwana z kontekstu.
 */
public interface TokenVersionLookup {

    /** Pusty wynik = konto nie istnieje (np. usuniete po wydaniu tokenu) -> traktowane jak niezgodnosc. */
    Optional<Integer> currentVersion(UUID accountId);

    /** Wsadowy odczyt (sweep sesji STOMP) - brak wpisu dla danego id = konto nie istnieje. */
    Map<UUID, Integer> currentVersions(Collection<UUID> accountIds);
}
