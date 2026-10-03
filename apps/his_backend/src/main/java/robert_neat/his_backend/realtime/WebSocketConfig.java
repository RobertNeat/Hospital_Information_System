package robert_neat.his_backend.realtime;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import robert_neat.his_backend.security.TokenVersionLookup;

/**
 * STOMP po czystym WebSocket (bez SockJS - kontrakt go nie wymaga): endpoint `/ws`, prosty broker `/topic` i
 * `/queue`, prefiks aplikacji `/app` (bez handlerow - kontrakt nie przewiduje komunikatow klient -> serwer) oraz
 * `/user` dla adresowania po uzytkowniku. Heartbeat i ponowienia polaczen: domyslne ustawienia brokera.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
@EnableScheduling
@EnableConfigurationProperties(WebSocketProperties.class)
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final String ENDPOINT = "/ws";

    private final WebSocketProperties properties;
    private final JwtDecoder jwtDecoder;
    private final TokenVersionLookup tokenVersions;
    private final StompSessionRegistry sessions;

    WebSocketConfig(WebSocketProperties properties, JwtDecoder jwtDecoder, TokenVersionLookup tokenVersions,
            StompSessionRegistry sessions) {
        this.properties = properties;
        this.jwtDecoder = jwtDecoder;
        this.tokenVersions = tokenVersions;
        this.sessions = sessions;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT).setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix(StompDestinations.USER_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompSecurityInterceptor(jwtDecoder, tokenVersions, sessions));
    }
}
