package robert_neat.his_backend.realtime;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dozwolone originy handshake WebSocket (`his.websocket.allowed-origins`, po przecinku). Pusta lista = tylko
 * same-origin (domyslne zachowanie Springa; frontend i backend sa za jednym nginx).
 */
@ConfigurationProperties("his.websocket")
public record WebSocketProperties(List<String> allowedOrigins) {

    public WebSocketProperties {
        allowedOrigins = allowedOrigins == null ? List.of()
                : allowedOrigins.stream().filter(o -> o != null && !o.isBlank()).toList();
    }
}
