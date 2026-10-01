package robert_neat.eimaging.his;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguracja klienta FHIR do his_backend. Docelowo mTLS: wystarczy ustawic `sslBundle` na nazwe paczki
 * `spring.ssl.bundle.*` (np. z profilu `mtls`); klucz uslugowy zostaje do czasu zastapienia go certyfikatem.
 */
@ConfigurationProperties(prefix = "eimaging.his")
public record HisProperties(
        Boolean enabled,
        String baseUrl,
        String serviceKey,
        Duration connectTimeout,
        Duration readTimeout,
        String sslBundle) {

    public HisProperties {
        enabled = enabled != null && enabled;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:10420/fhir" : baseUrl;
        serviceKey = serviceKey == null ? "" : serviceKey;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
        sslBundle = sslBundle == null ? "" : sslBundle.trim();
    }
}
