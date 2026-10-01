package robert_neat.his_backend.lab.elab;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Klient FHIR do uslugi e-laboratory. Domyslnie wylaczony (brak e-laboratory nie moze blokowac zlecania badan).
 * Docelowo mTLS: wystarczy ustawic `sslBundle` na nazwe paczki `spring.ssl.bundle.*` (np. `fhir-client` z profilu `mtls`).
 */
@ConfigurationProperties(prefix = "his.integration.elab")
public record ELabProperties(
        Boolean enabled,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String sslBundle) {

    public ELabProperties {
        enabled = enabled != null && enabled;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:10422/fhir" : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
        sslBundle = sslBundle == null ? "" : sslBundle.trim();
    }
}
