package robert_neat.his_backend.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ustawienia tokenu JWT (HS256). `secret` jest wymagany (min. 32 bajty UTF-8); brak wartosci domyslnej
 * poza profilem `dev` i testami - walidacja przy starcie w {@link SecurityConfig}.
 */
@ConfigurationProperties("his.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("8h") Duration ttl,
        @DefaultValue("his-backend") String issuer) {
}
