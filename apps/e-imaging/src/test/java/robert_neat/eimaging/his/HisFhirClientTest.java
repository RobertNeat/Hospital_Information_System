package robert_neat.eimaging.his;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ca.uhn.fhir.context.FhirContext;
import robert_neat.eimaging.fhir.DiagnosticReportMapper;
import robert_neat.eimaging.fhir.ServiceRequestMapper;
import robert_neat.eimaging.order.HisSync.Outcome;
import robert_neat.eimaging.order.ImagingOrder;
import robert_neat.eimaging.order.ImagingOrderStatus;
import robert_neat.eimaging.order.OrderFixtures;
import robert_neat.eimaging.order.ResultStatus;

class HisFhirClientTest {

    private static final String BASE = "http://his.test/fhir";
    private static final String HIS_ID = "7f1c7f0e-0000-4000-8000-000000000001";
    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

    private final FhirContext fhir = FhirContext.forR4Cached();
    private final ServiceRequestMapper requests = new ServiceRequestMapper(fhir);
    private final DiagnosticReportMapper reports = new DiagnosticReportMapper(fhir);

    private MockRestServiceServer server;

    private HisFhirClient client(boolean enabled) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        HisProperties props = new HisProperties(enabled, BASE, "secret", Duration.ofSeconds(1),
                Duration.ofSeconds(1), "");
        return new HisFhirClient(props, requests, reports, builder.build());
    }

    private final ImagingOrder order = OrderFixtures.order(OrderFixtures.draft(HIS_ID, ImagingOrderStatus.SCHEDULED));

    @Test
    void putsStatusWithServiceKeyToHisOrderId() {
        HisFhirClient client = client(true);
        server.expect(requestTo(BASE + "/ServiceRequest/" + HIS_ID))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Service-Key", "secret"))
                .andExpect(header("Content-Type", "application/fhir+json"))
                .andExpect(content().string(Matchers.containsString("\"revoked\"")))
                .andExpect(content().string(Matchers.containsString("\"cancelled\"")))
                .andRespond(withSuccess("{\"resourceType\":\"ServiceRequest\"}", FHIR_JSON));

        assertThat(client.pushStatus(order, ImagingOrderStatus.CANCELLED).outcome()).isEqualTo(Outcome.SYNCED);
        server.verify();
    }

    @Test
    void postsDiagnosticReportWithFindingsConclusionAndCriticalFlag() {
        HisFhirClient client = client(true);
        server.expect(requestTo(BASE + "/DiagnosticReport"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Key", "secret"))
                .andExpect(content().string(Matchers.containsString("\"DiagnosticReport\"")))
                .andExpect(content().string(Matchers.containsString("ServiceRequest/" + HIS_ID)))
                .andExpect(content().string(Matchers.containsString("urn:his:imaging-exam")))
                .andExpect(content().string(Matchers.containsString("\"RTG-KOL\"")))
                .andExpect(content().string(Matchers.containsString("urn:his:fhir:imaging-findings")))
                .andExpect(content().string(Matchers.containsString("Opis badania.")))
                .andExpect(content().string(Matchers.containsString("\"conclusion\":\"Wniosek.\"")))
                .andExpect(content().string(Matchers.containsString("\"valueBoolean\":true")))
                .andExpect(content().string(Matchers.containsString("\"final\"")))
                .andRespond(withStatus(HttpStatus.CREATED));

        assertThat(client.pushResult(order, OrderFixtures.entry(ResultStatus.FINAL, true)).outcome())
                .isEqualTo(Outcome.SYNCED);
        server.verify();
    }

    @Test
    void clientErrorIsRejectionWithDiagnostics() {
        HisFhirClient client = client(true);
        server.expect(requestTo(BASE + "/ServiceRequest/" + HIS_ID))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(FHIR_JSON)
                        .body("{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\","
                                + "\"code\":\"conflict\",\"diagnostics\":\"Zlecenie jest w stanie koncowym\"}]}"));

        var result = client.pushStatus(order, ImagingOrderStatus.CANCELLED);
        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(result.message()).contains("409").contains("Zlecenie jest w stanie koncowym");
    }

    @Test
    void authAndServerErrorsAreRetryableUnavailable() {
        HisFhirClient client = client(true);
        server.expect(requestTo(BASE + "/ServiceRequest/" + HIS_ID)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThat(client.pushStatus(order, ImagingOrderStatus.CANCELLED).outcome()).isEqualTo(Outcome.UNAVAILABLE);

        server.reset();
        server.expect(requestTo(BASE + "/DiagnosticReport")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThat(client.pushResult(order, OrderFixtures.entry(ResultStatus.FINAL, false)).outcome())
                .isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    void networkFailureIsUnavailable() {
        HisFhirClient client = client(true);
        server.expect(requestTo(BASE + "/ServiceRequest/" + HIS_ID)).andRespond(request -> {
            throw new java.net.SocketTimeoutException("timeout");
        });
        assertThat(client.pushStatus(order, ImagingOrderStatus.CANCELLED).outcome()).isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    void disabledIntegrationMakesNoCalls() {
        HisFhirClient client = client(false);
        assertThat(client.pushStatus(order, ImagingOrderStatus.CANCELLED).outcome()).isEqualTo(Outcome.DISABLED);
        server.verify();
    }
}
