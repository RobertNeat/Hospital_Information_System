package robert_neat.his_backend.realtime;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Dozwolone originy handshake WebSocket (`his.websocket.allowed-origins`, po przecinku). Pusta lista = tylko
 * same-origin (domyslne zachowanie Springa; frontend i backend sa za jednym nginx).
 * <p>
 * {@code tokenVersionSweepInterval} - co ile {@link StompTokenVersionSweeper} re-sprawdza wersje tokenu
 * aktywnych sesji STOMP (pojedyncza instancja brokera w pamieci - architektura nie przewiduje wielu wezlow).
 */
@ConfigurationProperties("his.websocket")
public record WebSocketProperties(List<String> allowedOrigins, @DefaultValue("30s") Duration tokenVersionSweepInterval) {

    public WebSocketProperties {
        allowedOrigins = allowedOrigins == null ? List.of()
                : allowedOrigins.stream().filter(o -> o != null && !o.isBlank()).toList();
    }
}
