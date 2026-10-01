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
 * wielu instancjach backendu wymagalby wspolnego magazynu. Zasilany z pakietu `realtime`.
 */
@Component
public class PresenceRegistry {

    private final Map<String, UUID> staffBySession = new ConcurrentHashMap<>();

    public void connected(String sessionId, UUID staffId) {
        staffBySession.put(sessionId, staffId);
    }

    public void disconnected(String sessionId) {
        staffBySession.remove(sessionId);
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
