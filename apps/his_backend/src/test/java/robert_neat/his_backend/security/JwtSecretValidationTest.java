package robert_neat.his_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class JwtSecretValidationTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void missingOrShortSecretFailsWithReadableMessage() {
        for (String secret : new String[] {null, "", "za-krotki-klucz"}) {
            assertThatThrownBy(() -> config.jwtSecretKey(new JwtProperties(secret, Duration.ofHours(8), "his-backend")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("HIS_JWT_SECRET")
                    .hasMessageContaining("32");
        }
    }

    @Test
    void secretOfAtLeast32BytesIsAccepted() {
        assertThat(config.jwtSecretKey(new JwtProperties("x".repeat(32), Duration.ofHours(8), "his-backend")))
                .isNotNull();
    }
}
