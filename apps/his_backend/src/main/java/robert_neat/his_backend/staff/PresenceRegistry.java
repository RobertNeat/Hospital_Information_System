package robert_neat.his_backend.staff;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Obecnosc pracownikow (`StaffMember.online`): pracownik jest online, gdy ma co najmniej jedna otwarta sesje STOMP
 * (po udanym CONNECT, do DISCONNECT/zamkniecia). Stan wylacznie w pamieci tej instancji (bez persystencji); przy
 * wielu instancjach backendu wymagalby wspolnego magazynu. Zasilany z pakietu `realtime`, ktory tez rozglasza
 * zwrocone przejscia (`PresenceChanged`) - stad metody zwracaja, czy dany pracownik faktycznie zmienil stan
 * (pierwsza sesja / ostatnia sesja), a nie tylko sukces operacji na mapie.
 */
@Component
public class PresenceRegistry {

    private final Map<String, UUID> staffBySession = new ConcurrentHashMap<>();

    /** @return true, jesli to pierwsza sesja tego pracownika (przejscie offline -> online). */
    public synchronized boolean connected(String sessionId, UUID staffId) {
        boolean wasOffline = !staffBySession.containsValue(staffId);
        staffBySession.put(sessionId, staffId);
        return wasOffline;
    }

    /**
     * @return identyfikator pracownika, jesli to byla jego ostatnia sesja (przejscie online -> offline);
     *         {@code null}, gdy sesja juz byla usunieta (np. podwojny DISCONNECT) albo pracownik ma inne sesje.
     */
    public synchronized UUID disconnected(String sessionId) {
        UUID staffId = staffBySession.remove(sessionId);
        if (staffId == null || staffBySession.containsValue(staffId)) {
            return null;
        }
        return staffId;
    }

    public boolean isOnline(UUID staffId) {
        return staffBySession.containsValue(staffId);
    }

    /** Migawka identyfikatorow pracownikow online. */
    public Set<UUID> onlineStaffIds() {
        Collection<UUID> values = staffBySession.values();
        return values.stream().collect(Collectors.toUnmodifiableSet());
    }
}
