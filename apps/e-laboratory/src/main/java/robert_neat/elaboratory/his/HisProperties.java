package robert_neat.elaboratory.his;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguracja klienta FHIR do his_backend. mTLS: `sslBundle` = nazwa paczki `spring.ssl.bundle.*` (profil `mtls`
 * ustawia `mtls`); uwierzytelnienie to certyfikat klienta z paczki.
 */
@ConfigurationProperties(prefix = "elaboratory.his")
public record HisProperties(
        Boolean enabled,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String sslBundle) {

    public HisProperties {
        enabled = enabled != null && enabled;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:10420/fhir" : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
        sslBundle = sslBundle == null ? "" : sslBundle.trim();
    }
}
