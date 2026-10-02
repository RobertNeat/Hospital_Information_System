package robert_neat.his_backend.imaging.eimg;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import robert_neat.his_backend.common.fhir.FhirSystems;

/**
 * Klient FHIR e-imaging (Spring `RestClient`, tresc jako String: serializacja po stronie HAPI). Polaczenie dopiero
 * przy pierwszym wywolaniu. Bledy sieci/HTTP to {@code RestClientException} - obsluguje je {@link EImgIntegration}.
 * mTLS: paczka SSL z `his.integration.eimg.ssl-bundle`.
 */
@Component
public class EImgClient {

    private final RestClient restClient;

    @Autowired
    public EImgClient(EImgProperties properties, SslBundles sslBundles) {
        this(buildRestClient(properties, sslBundles));
    }

    EImgClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(EImgProperties p, SslBundles sslBundles) {
        HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(p.connectTimeout(), p.readTimeout());
        if (!p.sslBundle().isEmpty()) {
            settings = settings.withSslBundle(sslBundles.getBundle(p.sslBundle()));
        }
        return RestClient.builder().baseUrl(p.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings)).build();
    }

    /** `POST /ServiceRequest`: nowe zlecenie (e-imaging jest idempotentne wzgledem id zlecenia). */
    public void submit(String serviceRequestJson) {
        restClient.post().uri("/ServiceRequest")
                .contentType(FhirSystems.FHIR_JSON)
                .accept(FhirSystems.FHIR_JSON, MediaType.APPLICATION_JSON)
                .body(serviceRequestJson).retrieve().toBodilessEntity();
    }

    /** `PUT /ServiceRequest/{orderId}`: zmiana statusu zlecenia zainicjowana w HIS (dowolne przejscie, w tym anulowanie). */
    public void updateStatus(String orderId, String serviceRequestJson) {
        restClient.put().uri("/ServiceRequest/{id}", orderId)
                .contentType(FhirSystems.FHIR_JSON)
                .accept(FhirSystems.FHIR_JSON, MediaType.APPLICATION_JSON)
                .body(serviceRequestJson).retrieve().toBodilessEntity();
    }
}
