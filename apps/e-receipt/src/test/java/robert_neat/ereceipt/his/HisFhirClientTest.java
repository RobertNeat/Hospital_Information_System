package robert_neat.ereceipt.his;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ca.uhn.fhir.context.FhirContext;
import robert_neat.ereceipt.fhir.MedicationRequestMapper;
import robert_neat.ereceipt.prescription.HisSync.Outcome;
import robert_neat.ereceipt.prescription.Receipt;
import robert_neat.ereceipt.prescription.ReceiptStatus;

class HisFhirClientTest {

    private static final String BASE = "http://his.test/fhir";
    private static final String HIS_ID = "7f1c7f0e-0000-4000-8000-000000000001";

    private final MedicationRequestMapper mapper = new MedicationRequestMapper(FhirContext.forR4Cached());
    private final Receipt receipt = new Receipt("K".repeat(44), HIS_ID, "1234", "Patient/p", "Practitioner/s",
            Instant.parse("2026-10-01T10:00:00Z"), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"),
            "Lek", List.of("1 tabl."), null, Instant.now());

    private MockRestServiceServer server;

    private HisFhirClient client(boolean enabled) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        HisProperties props = new HisProperties(enabled, BASE, "secret", Duration.ofSeconds(1),
                Duration.ofSeconds(1), "");
        return new HisFhirClient(props, mapper, builder.build());
    }

    @Test
    void putsStatusWithServiceKeyToHisPrescriptionId() {
        HisFhirClient client = client(true);
        server.expect(requestTo(BASE + "/MedicationRequest/" + HIS_ID))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Service-Key", "secret"))
                .andExpect(header("Content-Type", "application/fhir+json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"completed\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"dispensed\"")))
                .andRespond(withSuccess("{\"resourceType\":\"MedicationRequest\"}",
                        MediaType.valueOf("application/fhir+json")));

        assertThat(client.pushStatus(receipt, ReceiptStatus.DISPENSED).outcome()).isEqualTo(Outcome.SYNCED);
        server.verify();
    }

    @Test
    void clientErrorIsRejectionWithDiagnostics() {
        HisFhirClient client = client(true);
        server.expect(requestTo(BASE + "/MedicationRequest/" + HIS_ID))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.valueOf("application/fhir+json"))
                        .body("{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\","
                                + "\"code\":\"conflict\",\"diagnostics\":\"Recepta jest anulowana\"}]}"));

        var result = client.pushStatus(receipt, ReceiptStatus.DISPENSED);
        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(result.message()).contains("409").contains("Recepta jest anulowana");
    }

    @Test
    void authAndServerErrorsAreRetryableUnavailable() {
        HisFhirClient client = client(true);
        server.expect(requestTo(BASE + "/MedicationRequest/" + HIS_ID)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThat(client.pushStatus(receipt, ReceiptStatus.CANCELLED).outcome()).isEqualTo(Outcome.UNAVAILABLE);

        server.reset();
        server.expect(requestTo(BASE + "/MedicationRequest/" + HIS_ID))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThat(client.pushStatus(receipt, ReceiptStatus.CANCELLED).outcome()).isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    void networkFailureIsUnavailable() {
        HisFhirClient client = client(true);
        server.expect(requestTo(BASE + "/MedicationRequest/" + HIS_ID))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("timeout");
                });
        assertThat(client.pushStatus(receipt, ReceiptStatus.CANCELLED).outcome()).isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    void disabledClientDoesNotCallHis() {
        HisFhirClient client = client(false);
        assertThat(client.pushStatus(receipt, ReceiptStatus.CANCELLED).outcome()).isEqualTo(Outcome.DISABLED);
        server.verify();
    }
}
