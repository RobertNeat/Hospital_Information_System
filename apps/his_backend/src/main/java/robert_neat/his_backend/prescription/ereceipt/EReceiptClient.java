package robert_neat.his_backend.prescription.ereceipt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import robert_neat.his_backend.common.fhir.FhirSystems;

/**
 * Klient FHIR e-receipt (Spring `RestClient`, tresc jako String: serializacja po stronie HAPI). Polaczenie dopiero
 * przy pierwszym wywolaniu, wiec backend startuje bez e-receipt. Bledy sieci/HTTP to {@code RestClientException}
 * - obsluguje je {@link EReceiptIntegration}. mTLS: paczka SSL z `his.integration.ereceipt.ssl-bundle`.
 */
@Component
public class EReceiptClient {

    private final RestClient restClient;

    @Autowired
    public EReceiptClient(EReceiptProperties properties, SslBundles sslBundles) {
        this(buildRestClient(properties, sslBundles));
    }

    EReceiptClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(EReceiptProperties p, SslBundles sslBundles) {
        HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(p.connectTimeout(), p.readTimeout());
        if (!p.sslBundle().isEmpty()) {
            settings = settings.withSslBundle(sslBundles.getBundle(p.sslBundle()));
        }
        return RestClient.builder().baseUrl(p.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings)).build();
    }

    /** `POST /MedicationRequest`: zwraca cialo odpowiedzi (zasob z kluczem `eRxKey`). */
    public String submit(String medicationRequestJson) {
        return restClient.post().uri("/MedicationRequest")
                .contentType(FhirSystems.FHIR_JSON)
                .accept(FhirSystems.FHIR_JSON, MediaType.APPLICATION_JSON)
                .body(medicationRequestJson).retrieve().body(String.class);
    }

    /** `PUT /MedicationRequest/{eRxKey}`: zmiana stanu zainicjowana w HIS (np. anulowanie). */
    public void updateStatus(String eRxKey, String medicationRequestJson) {
        restClient.put().uri("/MedicationRequest/{id}", eRxKey)
                .contentType(FhirSystems.FHIR_JSON)
                .accept(FhirSystems.FHIR_JSON, MediaType.APPLICATION_JSON)
                .body(medicationRequestJson).retrieve().toBodilessEntity();
    }
}
