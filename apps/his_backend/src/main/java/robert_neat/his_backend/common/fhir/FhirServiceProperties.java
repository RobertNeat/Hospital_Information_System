package robert_neat.his_backend.common.fhir;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Autoryzacja wywolan FHIR z uslug e-*: certyfikat klienta zaufany przez truststore (mTLS) i CN z listy dozwolonych.
 * Pusta lista = `/fhir/**` odrzuca kazde zadanie (401).
 */
@ConfigurationProperties(prefix = "his.fhir")
public record FhirServiceProperties(List<String> allowedClientCns) {

    public FhirServiceProperties {
        allowedClientCns = allowedClientCns == null ? List.of() : List.copyOf(allowedClientCns);
    }
}
