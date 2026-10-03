package robert_neat.his_backend.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ustawienia tokenu JWT (HS256). `secret` jest wymagany (min. 32 bajty UTF-8); brak wartosci domyslnej
 * poza profilem `dev` i testami - walidacja przy starcie w {@link SecurityConfig}.
 * <p>
 * `ttl` jest celowo krotkie (token dostepu, nie sesja): odwolanie dziala natychmiast przez `token_version`
 * (patrz {@link HisJwtAuthenticationConverter}), ale bez niego blokada/zmiana roli dzialalaby dopiero po
 * `exp`, stad TTL rzedu minut, nie godzin. Dlugie sesje: frontend odswieza token przez `POST /auth/refresh`
 * przed wygasnieciem.
 */
@ConfigurationProperties("his.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("15m") Duration ttl,
        @DefaultValue("his-backend") String issuer) {
}
