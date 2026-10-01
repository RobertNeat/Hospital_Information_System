package robert_neat.his_backend.prescription.ereceipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.MedicationRequest;
import robert_neat.his_backend.prescription.PrescriptionStatus;

class EReceiptClientTest {

    private static final String BASE = "http://e-receipt.test/fhir";
    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

    private MockRestServiceServer server;
    private EReceiptClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EReceiptClient(builder.build());
    }

    @Test
    void submitPostsFhirJsonAndReturnsResponseBody() {
        server.expect(requestTo(BASE + "/MedicationRequest")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/fhir+json"))
                .andExpect(content().string("{\"resourceType\":\"MedicationRequest\"}"))
                .andRespond(withSuccess("{\"id\":\"KEY\"}", FHIR_JSON));

        assertThat(client.submit("{\"resourceType\":\"MedicationRequest\"}")).isEqualTo("{\"id\":\"KEY\"}");
        server.verify();
    }

    @Test
    void updateStatusPutsToErxKey() {
        server.expect(requestTo(BASE + "/MedicationRequest/KEY123")).andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        client.updateStatus("KEY123", "{}");
        server.verify();
    }

    @Test
    void httpErrorsSurfaceAsRestClientException() {
        server.expect(requestTo(BASE + "/MedicationRequest")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.submit("{}")).isInstanceOf(RestClientException.class);
    }

    @Test
    void mapperReadsErxKeyFromIdentifierThenFromId() {
        PrescriptionFhirMapper mapper = new PrescriptionFhirMapper(FhirContext.forR4Cached());
        assertThat(mapper.eRxKeyOf("""
                {"resourceType":"MedicationRequest","id":"IDKEY","status":"active","intent":"order",
                 "identifier":[{"system":"urn:his:erx-key","value":"IDENTKEY"}]}""")).contains("IDENTKEY");
        assertThat(mapper.eRxKeyOf("""
                {"resourceType":"MedicationRequest","id":"IDKEY","status":"active","intent":"order"}"""))
                .contains("IDKEY");
        assertThat(mapper.eRxKeyOf("""
                {"resourceType":"MedicationRequest","status":"active","intent":"order"}""")).isEmpty();
    }

    @Test
    void mapperStatusMappingIsSymmetric() {
        PrescriptionFhirMapper mapper = new PrescriptionFhirMapper(FhirContext.forR4Cached());
        for (PrescriptionStatus s : PrescriptionStatus.values()) {
            MedicationRequest mr = new MedicationRequest();
            mr.setStatus(PrescriptionFhirMapper.fhirStatus(s));
            mr.addExtension(new org.hl7.fhir.r4.model.Extension("urn:his:fhir:prescription-status",
                    new org.hl7.fhir.r4.model.StringType(s.wire())));
            assertThat(mapper.statusOf(mr)).isEqualTo(s);
        }
    }
}
