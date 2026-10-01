package robert_neat.his_backend.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** CORS jest domyslnie wylaczony (jedna origin przez nginx); niepusta lista wlacza go dla podanych origin. */
@ConfigurationProperties("his.security.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of()
                : allowedOrigins.stream().filter(o -> o != null && !o.isBlank()).toList();
    }
}
