package robert_neat.his_backend.imaging.eimg;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Klient FHIR do uslugi e-imaging. Domyslnie wylaczony (brak e-imaging nie moze blokowac zlecania badan).
 * Docelowo mTLS: wystarczy ustawic `sslBundle` na nazwe paczki `spring.ssl.bundle.*` (np. `fhir-client` z profilu `mtls`).
 */
@ConfigurationProperties(prefix = "his.integration.eimg")
public record EImgProperties(
        Boolean enabled,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String sslBundle) {

    public EImgProperties {
        enabled = enabled != null && enabled;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:10423/fhir" : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
        sslBundle = sslBundle == null ? "" : sslBundle.trim();
    }
}
