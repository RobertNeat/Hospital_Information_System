package robert_neat.his_backend.realtime;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import robert_neat.his_backend.staff.PresenceRegistry;
import robert_neat.his_backend.staff.events.PresenceChanged;

/**
 * Sesje STOMP -> {@link PresenceRegistry}. Publikuje {@link PresenceChanged} wylacznie przy faktycznej zmianie
 * stanu pracownika (pierwsza sesja / ostatnia sesja) - `realtime/RealtimePublisher` rozglasza to na
 * `/topic/presence`.
 */
@Component
class PresenceEventListener {

    private final PresenceRegistry presence;
    private final ApplicationEventPublisher events;
    private final StompSessionRegistry sessions;

    PresenceEventListener(PresenceRegistry presence, ApplicationEventPublisher events, StompSessionRegistry sessions) {
        this.presence = presence;
        this.events = events;
        this.sessions = sessions;
    }

    @EventListener
    void on(SessionConnectedEvent event) {
        if (event.getUser() instanceof StompUserAuthentication user) {
            boolean becameOnline = presence.connected(SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders()),
                    user.staffId());
            if (becameOnline) {
                events.publishEvent(new PresenceChanged(user.staffId(), true, Instant.now()));
            }
        }
    }

    @EventListener
    void on(SessionDisconnectEvent event) {
        sessions.remove(event.getSessionId());
        UUID becameOffline = presence.disconnected(event.getSessionId());
        if (becameOffline != null) {
            events.publishEvent(new PresenceChanged(becameOffline, false, Instant.now()));
        }
    }
}
