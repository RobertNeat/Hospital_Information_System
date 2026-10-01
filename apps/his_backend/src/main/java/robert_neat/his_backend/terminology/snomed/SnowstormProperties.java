package robert_neat.his_backend.terminology.snomed;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Konfiguracja klienta Snowstorm Lite (FHIR terminology server). */
@ConfigurationProperties(prefix = "his.terminology.snowstorm")
public record SnowstormProperties(
        Boolean enabled,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String displayLanguage,
        Integer maxPageSize) {

    public SnowstormProperties {
        enabled = enabled == null || enabled;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8080/fhir" : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        displayLanguage = displayLanguage == null || displayLanguage.isBlank() ? "pl,en" : displayLanguage;
        maxPageSize = maxPageSize == null || maxPageSize < 1 ? 100 : maxPageSize;
    }
}
