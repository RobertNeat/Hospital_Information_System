package robert_neat.eimaging.his;

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

import robert_neat.eimaging.fhir.DiagnosticReportMapper;
import robert_neat.eimaging.fhir.FhirSystems;
import robert_neat.eimaging.fhir.ServiceRequestMapper;
import robert_neat.eimaging.order.HisSync;
import robert_neat.eimaging.order.ImagingOrder;
import robert_neat.eimaging.order.ImagingOrderStatus;
import robert_neat.eimaging.order.ResultEntry;

/**
 * Wywolania FHIR do his_backend: `PUT {base}/ServiceRequest/{hisOrderId}` (stan
 * zlecenia) i `POST {base}/DiagnosticReport` (wynik). 4xx (poza 401/403) = odrzucenie przez HIS; 401/403, 5xx i bledy
 * sieci = "niedostepny" (zmiana do ponowienia).
 */
@Component
class HisFhirClient implements HisSync {

    private static final Logger log = LoggerFactory.getLogger(HisFhirClient.class);

    private final HisProperties properties;
    private final ServiceRequestMapper requests;
    private final DiagnosticReportMapper reports;
    private final RestClient restClient;

    @Autowired
    HisFhirClient(HisProperties properties, ServiceRequestMapper requests, DiagnosticReportMapper reports,
            SslBundles sslBundles) {
        this(properties, requests, reports, buildRestClient(properties, sslBundles));
    }

    HisFhirClient(HisProperties properties, ServiceRequestMapper requests, DiagnosticReportMapper reports,
            RestClient restClient) {
        this.properties = properties;
        this.requests = requests;
        this.reports = reports;
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
    public Result pushStatus(ImagingOrder order, ImagingOrderStatus target) {
        if (!properties.enabled()) {
            return Result.of(Outcome.DISABLED);
        }
        String body = requests.encode(requests.toResource(order, target));
        return call("zlecenia " + order.getHisOrderId(), () -> restClient.put()
                .uri("/ServiceRequest/{id}", order.getHisOrderId()).contentType(FhirSystems.FHIR_JSON)
                .accept(FhirSystems.FHIR_JSON, MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity());
    }

    @Override
    public Result pushResult(ImagingOrder order, ResultEntry result) {
        if (!properties.enabled()) {
            return Result.of(Outcome.DISABLED);
        }
        String body = reports.encode(order, result);
        return call("wyniku zlecenia " + order.getHisOrderId(), () -> restClient.post().uri("/DiagnosticReport")
                .contentType(FhirSystems.FHIR_JSON).accept(FhirSystems.FHIR_JSON, MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity());
    }

    private Result call(String what, Runnable request) {
        try {
            request.run();
            return Result.of(Outcome.SYNCED);
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            log.warn("HIS odrzucil/nie przyjal zmiany {} (HTTP {})", what, status);
            if (e.getStatusCode().is4xxClientError() && status != 401 && status != 403) {
                return new Result(Outcome.REJECTED,
                        "HTTP " + status + " " + requests.diagnostics(e.getResponseBodyAsString()));
            }
            return new Result(Outcome.UNAVAILABLE, "HIS zwrocil HTTP " + status);
        } catch (RestClientException e) {
            log.warn("HIS niedostepny przy zmianie {}: {}", what, e.getMessage());
            return new Result(Outcome.UNAVAILABLE, "HIS niedostepny");
        }
    }
}
