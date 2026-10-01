package robert_neat.his_backend.prescription.ereceipt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Klient FHIR do usługi e-receipt. Domyslnie wylaczony (brak e-receipt nie moze blokowac wystawiania recept).
 * Docelowo mTLS: wystarczy ustawic `sslBundle` na nazwe paczki `spring.ssl.bundle.*` (np. `fhir-client` z profilu `mtls`).
 */
@ConfigurationProperties(prefix = "his.integration.ereceipt")
public record EReceiptProperties(
        Boolean enabled,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String sslBundle) {

    public EReceiptProperties {
        enabled = enabled != null && enabled;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:10421/fhir" : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
        sslBundle = sslBundle == null ? "" : sslBundle.trim();
    }
}
