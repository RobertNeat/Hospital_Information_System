package robert_neat.his_backend.realtime;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import robert_neat.his_backend.staff.PresenceRegistry;

/**
 * Sesje STOMP -> {@link PresenceRegistry}. Zmiana obecnosci nie jest rozglaszana (API.md, par. 10 nie przewiduje
 * tematu presence).
 */
@Component
class PresenceEventListener {

    private final PresenceRegistry presence;

    PresenceEventListener(PresenceRegistry presence) {
        this.presence = presence;
    }

    @EventListener
    void on(SessionConnectedEvent event) {
        if (event.getUser() instanceof StompUserAuthentication user) {
            presence.connected(SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders()),
                    user.staffId());
        }
    }

    @EventListener
    void on(SessionDisconnectEvent event) {
        presence.disconnected(event.getSessionId());
    }
}
