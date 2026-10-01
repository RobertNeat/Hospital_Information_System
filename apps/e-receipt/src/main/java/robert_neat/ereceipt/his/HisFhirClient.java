package robert_neat.ereceipt.his;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import robert_neat.ereceipt.fhir.FhirSystems;
import robert_neat.ereceipt.fhir.MedicationRequestMapper;
import robert_neat.ereceipt.prescription.HisSync;
import robert_neat.ereceipt.prescription.Receipt;
import robert_neat.ereceipt.prescription.ReceiptStatus;

/**
 * Wywolanie FHIR do his_backend: `PUT {base}/MedicationRequest/{hisPrescriptionId}`.
 * 4xx (poza 401/403) = odrzucenie zmiany przez HIS; 401/403, 5xx i bledy sieci = "niedostepny" (zmiana do ponowienia).
 */
@Component
class HisFhirClient implements HisSync {

    private static final Logger log = LoggerFactory.getLogger(HisFhirClient.class);

    private final HisProperties properties;
    private final MedicationRequestMapper mapper;
    private final RestClient restClient;

    @Autowired
    HisFhirClient(HisProperties properties, MedicationRequestMapper mapper, SslBundles sslBundles) {
        this(properties, mapper, buildRestClient(properties, sslBundles));
    }

    HisFhirClient(HisProperties properties, MedicationRequestMapper mapper, RestClient restClient) {
        this.properties = properties;
        this.mapper = mapper;
        this.restClient = restClient;
    }

    /** Fabryka z limitami czasu i opcjonalna paczka SSL (mTLS); polaczenie nawiazywane dopiero przy pierwszym wywolaniu. */
    private static RestClient buildRestClient(HisProperties p, SslBundles sslBundles) {
        HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(p.connectTimeout(), p.readTimeout());
        if (!p.sslBundle().isEmpty()) {
            settings = settings.withSslBundle(sslBundles.getBundle(p.sslBundle()));
        }
        return RestClient.builder().baseUrl(p.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings)).build();
    }

    @Override
    public Result pushStatus(Receipt receipt, ReceiptStatus target) {
        if (!properties.enabled()) {
            return Result.of(Outcome.DISABLED);
        }
        String body = mapper.encode(mapper.toResource(receipt, target));
        try {
            restClient.put().uri("/MedicationRequest/{id}", receipt.getHisPrescriptionId())
                    .contentType(FhirSystems.FHIR_JSON)
                    .accept(FhirSystems.FHIR_JSON, MediaType.APPLICATION_JSON)
                    .body(body).retrieve().toBodilessEntity();
            return Result.of(Outcome.SYNCED);
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            log.warn("HIS odrzucil/nie przyjal zmiany statusu recepty {} (HTTP {})", receipt.getErxKey(), status);
            if (e.getStatusCode().is4xxClientError() && status != 401 && status != 403) {
                return new Result(Outcome.REJECTED, "HTTP " + status + " " + mapper.diagnostics(e.getResponseBodyAsString()));
            }
            return new Result(Outcome.UNAVAILABLE, "HIS zwrocil HTTP " + status);
        } catch (RestClientException e) {
            log.warn("HIS niedostepny przy zmianie statusu recepty {}: {}", receipt.getErxKey(), e.getMessage());
            return new Result(Outcome.UNAVAILABLE, "HIS niedostepny");
        }
    }
}
