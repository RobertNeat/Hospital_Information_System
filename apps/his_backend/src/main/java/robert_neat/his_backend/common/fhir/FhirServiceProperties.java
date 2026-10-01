package robert_neat.his_backend.common.fhir;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Autoryzacja wywolan FHIR przychodzacych z uslug e-*: wspolny klucz w naglowku {@code X-Service-Key}.
 * Pusty klucz = endpointy `/fhir/**` odrzucaja kazde zadanie (401). Docelowo mTLS zastapi klucz.
 */
@ConfigurationProperties(prefix = "his.fhir")
public record FhirServiceProperties(String serviceKey) {

    public FhirServiceProperties {
        serviceKey = serviceKey == null ? "" : serviceKey;
    }
}
