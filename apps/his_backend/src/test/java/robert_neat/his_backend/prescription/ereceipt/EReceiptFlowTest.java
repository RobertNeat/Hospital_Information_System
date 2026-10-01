package robert_neat.his_backend.prescription.ereceipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.hl7.fhir.r4.model.MedicationRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;

import ca.uhn.fhir.context.FhirContext;
import robert_neat.his_backend.common.fhir.FhirTestAuth;
import robert_neat.his_backend.TestcontainersConfiguration;

/**
 * Przeplyw HIS <-> e-receipt na prawdziwych commitach (AFTER_COMMIT nie dziala w testach `@Transactional`):
 * atrapa e-receipt to wbudowany serwer JDK pod adresem z konfiguracji. Wlasny kontekst i kontener (nie dziedziczy po
 * `ApiIntegrationTest`), dane `reference,mock`; testy tworza nowe recepty, wiec nie zaleza od siebie.
 */
@SpringBootTest(properties = {"spring.liquibase.contexts=reference,mock", "his.integration.ereceipt.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EReceiptFlowTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");
    private static final String PATIENT = "50c8f3fa-ea66-581a-9207-f9c4c7131d26";
    private static final String POLPRIL = "24c2f3ff-06d4-53d2-9bc5-561b36774246";

    record Call(String method, String path, String body) {
    }

    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger KEY_SEQUENCE = new AtomicInteger();
    /** 0 = zachowanie normalne (201 + klucz), >0 = kod bledu, -1 = zerwane polaczenie, -2 = klucz o zlym formacie. */
    private static final AtomicInteger FAILURE = new AtomicInteger();
    private static final AtomicReference<String> LAST_KEY = new AtomicReference<>();
    private static final HttpServer FAKE_E_RECEIPT = startFake();

    @Autowired
    MockMvc mvc;
    @Autowired
    FhirContext fhir;

    private static HttpServer startFake() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/fhir/MedicationRequest", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                CALLS.add(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body));
                int failure = FAILURE.get();
                if (failure == -1) {
                    exchange.close();
                    return;
                }
                byte[] response = new byte[0];
                int code = failure;
                if (failure <= 0 && "POST".equals(exchange.getRequestMethod())) {
                    String key = failure == -2 ? "ZLY-FORMAT" : String.format("%044d", KEY_SEQUENCE.incrementAndGet());
                    LAST_KEY.set(key);
                    response = ("{\"resourceType\":\"MedicationRequest\",\"id\":\"" + key + "\",\"status\":\"active\","
                            + "\"intent\":\"order\",\"identifier\":[{\"system\":\"urn:his:erx-key\",\"value\":\""
                            + key + "\"}]}").getBytes(StandardCharsets.UTF_8);
                    code = 201;
                } else if (failure <= 0) {
                    code = 200;
                }
                exchange.getResponseHeaders().add("Content-Type", "application/fhir+json");
                exchange.sendResponseHeaders(code, response.length == 0 ? -1 : response.length);
                if (response.length > 0) {
                    exchange.getResponseBody().write(response);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void eReceiptUrl(DynamicPropertyRegistry registry) {
        registry.add("his.integration.ereceipt.base-url",
                () -> "http://127.0.0.1:" + FAKE_E_RECEIPT.getAddress().getPort() + "/fhir");
    }

    @AfterAll
    static void stopFake() {
        FAKE_E_RECEIPT.stop(0);
    }

    @BeforeEach
    void reset() {
        CALLS.clear();
        FAILURE.set(0);
    }

    // --- pomocnicze ---

    private String token() throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"employeeId\":\"doctor\",\"password\":\"doctor\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return "Bearer " + JsonPath.<String>read(response, "$.accessToken");
    }

    private String issue(String kind) throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String body = "{\"validFrom\":\"" + today + "\",\"validUntil\":\"" + today.plusDays(30) + "\",\"kind\":\""
                + kind + "\",\"items\":[{\"drugId\":\"" + POLPRIL + "\",\"dosage\":{\"dose\":5,\"doseUnit\":\"mg\","
                + "\"route\":\"oral\",\"frequency\":\"BID\",\"durationDays\":30,\"asNeeded\":false},"
                + "\"quantityPackages\":1,\"reimbursement\":\"30%\",\"substitutionAllowed\":true}]}";
        String response = mvc.perform(post("/api/v1/patients/{id}/prescriptions", PATIENT)
                        .header(HttpHeaders.AUTHORIZATION, token()).contentType(JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(response, "$.id");
    }

    private String storedKey(String id) throws Exception {
        String response = mvc.perform(get("/api/v1/prescriptions/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(response, "$.eRxKey");
    }

    // --- wystawienie ---

    @Test
    void issuedEPrescriptionIsSubmittedAndErxKeyReplaced() throws Exception {
        String id = issue("e_prescription");

        assertThat(CALLS).singleElement().satisfies(call -> {
            assertThat(call.method()).isEqualTo("POST");
            assertThat(call.path()).isEqualTo("/fhir/MedicationRequest");
            MedicationRequest sent = fhir.newJsonParser().parseResource(MedicationRequest.class, call.body());
            assertThat(sent.getIdentifier()).extracting(i -> i.getSystem() + "=" + i.getValue())
                    .contains("urn:his:prescription-id=" + id);
            assertThat(sent.getStatus()).isEqualTo(MedicationRequest.MedicationRequestStatus.ACTIVE);
            assertThat(sent.getMedicationCodeableConcept().getText()).isNotBlank();
        });
        assertThat(storedKey(id)).isEqualTo(LAST_KEY.get()).matches("\\d{44}");
    }

    @Test
    void keyUpdateKeepsVersionSoClientCanStillCancel() throws Exception {
        String id = issue("e_prescription");
        assertThat(storedKey(id)).isEqualTo(LAST_KEY.get());

        mvc.perform(post("/api/v1/prescriptions/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(JSON).content("{\"version\":0,\"reason\":\"pomylka\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("cancelled"));
    }

    @Test
    void hospitalOrdersAreNotSentToEReceipt() throws Exception {
        String id = issue("hospital_order");

        assertThat(CALLS).isEmpty();
        assertThat(storedKey(id)).doesNotMatch("\\d{44}");
    }

    @Test
    void eReceiptErrorDoesNotBlockIssuingAndKeepsLocalKey() throws Exception {
        FAILURE.set(500);
        String id = issue("e_prescription");

        assertThat(CALLS).hasSize(1);
        assertThat(storedKey(id)).doesNotMatch("\\d{44}");
    }

    @Test
    void eReceiptConnectionDropDoesNotBlockIssuing() throws Exception {
        FAILURE.set(-1);
        String id = issue("e_prescription");

        assertThat(CALLS).hasSize(1);
        assertThat(storedKey(id)).matches("[A-Z0-9]{44}").doesNotMatch("\\d{44}");
    }

    @Test
    void malformedKeyFromEReceiptIsIgnored() throws Exception {
        // klucz o zlym formacie nie zmiesci sie w `char(44)` - zostaje lokalny
        FAILURE.set(-2);
        String id = issue("e_prescription");

        assertThat(CALLS).hasSize(1);
        assertThat(storedKey(id)).matches("[A-Z0-9]{44}").isNotEqualTo("ZLY-FORMAT");
    }

    // --- anulowanie ---

    @Test
    void cancellingInHisIsPropagatedToEReceipt() throws Exception {
        String id = issue("e_prescription");
        String key = LAST_KEY.get();
        CALLS.clear();

        mvc.perform(post("/api/v1/prescriptions/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(JSON).content("{}"))
                .andExpect(status().isOk());

        assertThat(CALLS).singleElement().satisfies(call -> {
            assertThat(call.method()).isEqualTo("PUT");
            assertThat(call.path()).isEqualTo("/fhir/MedicationRequest/" + key);
            MedicationRequest sent = fhir.newJsonParser().parseResource(MedicationRequest.class, call.body());
            assertThat(sent.getStatus()).isEqualTo(MedicationRequest.MedicationRequestStatus.CANCELLED);
        });
    }

    @Test
    void cancellingInHisSucceedsWhenEReceiptFails() throws Exception {
        String id = issue("e_prescription");
        FAILURE.set(-1);

        mvc.perform(post("/api/v1/prescriptions/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("cancelled"));
    }

    @Test
    void cancellationInitiatedByEReceiptIsNotEchoedBack() throws Exception {
        String id = issue("e_prescription");
        String key = LAST_KEY.get();
        CALLS.clear();

        mvc.perform(put("/fhir/MedicationRequest/{id}", id).with(FhirTestAuth.service())
                        .contentType(FHIR_JSON)
                        .content("{\"resourceType\":\"MedicationRequest\",\"status\":\"cancelled\",\"intent\":\"order\","
                                + "\"identifier\":[{\"system\":\"urn:his:erx-key\",\"value\":\"" + key + "\"}],"
                                + "\"medicationCodeableConcept\":{\"text\":\"x\"}}"))
                .andExpect(status().isOk());

        assertThat(CALLS).isEmpty();
        mvc.perform(get("/api/v1/prescriptions/{id}", id).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.cancelReason").value("Anulowano w e-receipt"));
    }
}
