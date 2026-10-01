package robert_neat.his_backend.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Polityka blokady konta po kolejnych nieudanych probach logowania. */
@ConfigurationProperties("his.security.lockout")
public record LockoutProperties(
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("15m") Duration duration) {
}
