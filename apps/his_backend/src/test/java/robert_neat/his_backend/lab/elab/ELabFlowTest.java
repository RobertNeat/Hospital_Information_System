package robert_neat.his_backend.lab.elab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.hl7.fhir.r4.model.ServiceRequest;
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
 * Przeplyw HIS <-> e-laboratory na prawdziwych commitach (AFTER_COMMIT nie dziala w testach `@Transactional`):
 * atrapa e-laboratory to wbudowany serwer JDK pod adresem z konfiguracji. Wlasny kontekst i kontener; testy tworza nowe
 * zlecenia, wiec nie zaleza od siebie.
 */
@SpringBootTest(properties = {"spring.liquibase.contexts=reference,mock", "his.integration.elab.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ELabFlowTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");
    private static final String PATIENT = "c078186c-c437-5fa8-8a5b-a6bf8883f8bf";

    record Call(String method, String path, String body) {
    }

    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    /** 0 = normalnie, >0 = kod bledu, -1 = zerwane polaczenie. */
    private static final AtomicInteger FAILURE = new AtomicInteger();
    private static final HttpServer FAKE_E_LAB = startFake();

    @Autowired
    MockMvc mvc;
    @Autowired
    FhirContext fhir;

    private static HttpServer startFake() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/fhir/ServiceRequest", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                CALLS.add(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body));
                int failure = FAILURE.get();
                if (failure == -1) {
                    exchange.close();
                    return;
                }
                int code = failure > 0 ? failure : "POST".equals(exchange.getRequestMethod()) ? 201 : 200;
                exchange.sendResponseHeaders(code, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void eLabUrl(DynamicPropertyRegistry registry) {
        registry.add("his.integration.elab.base-url",
                () -> "http://127.0.0.1:" + FAKE_E_LAB.getAddress().getPort() + "/fhir");
    }

    @AfterAll
    static void stopFake() {
        FAKE_E_LAB.stop(0);
    }

    @BeforeEach
    void reset() {
        CALLS.clear();
        FAILURE.set(0);
    }

    private String token() throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"employeeId\":\"doctor\",\"password\":\"doctor\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return "Bearer " + JsonPath.<String>read(response, "$.accessToken");
    }

    private String order() throws Exception {
        String body = "{\"items\":[{\"testCode\":\"MORF\",\"specimenType\":\"blood\"}],\"urgency\":\"urgent\","
                + "\"fasting\":false,\"plannedCollectionAt\":\"2030-01-01T08:00:00Z\",\"clinicalInfo\":\"Kontrola.\"}";
        String response = mvc.perform(post("/api/v1/patients/{id}/lab-orders", PATIENT)
                        .header(HttpHeaders.AUTHORIZATION, token()).contentType(JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(response, "$.id");
    }

    private void cancelInHis(String id) throws Exception {
        mvc.perform(post("/api/v1/lab-orders/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(JSON).content("{\"reason\":\"pomylka\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("cancelled"));
    }

    @Test
    void createdOrderIsSubmittedAsServiceRequest() throws Exception {
        String id = order();

        assertThat(CALLS).singleElement().satisfies(call -> {
            assertThat(call.method()).isEqualTo("POST");
            assertThat(call.path()).isEqualTo("/fhir/ServiceRequest");
            ServiceRequest sent = fhir.newJsonParser().parseResource(ServiceRequest.class, call.body());
            assertThat(sent.getIdElement().getIdPart()).isEqualTo(id);
            assertThat(sent.getIdentifier()).extracting(i -> i.getSystem() + "=" + i.getValue())
                    .contains("urn:his:lab-order-id=" + id);
            assertThat(sent.getPriority()).isEqualTo(ServiceRequest.ServiceRequestPriority.URGENT);
            assertThat(sent.getOrderDetail()).singleElement()
                    .satisfies(d -> assertThat(d.getCodingFirstRep().getCode()).isEqualTo("MORF"));
        });
    }

    @Test
    void eLabErrorOrDropDoesNotBlockOrdering() throws Exception {
        FAILURE.set(500);
        order();
        FAILURE.set(-1);
        order();

        assertThat(CALLS).hasSize(2);
    }

    @Test
    void cancellingInHisIsPropagatedToELab() throws Exception {
        String id = order();
        CALLS.clear();

        cancelInHis(id);

        assertThat(CALLS).singleElement().satisfies(call -> {
            assertThat(call.method()).isEqualTo("PUT");
            assertThat(call.path()).isEqualTo("/fhir/ServiceRequest/" + id);
            ServiceRequest sent = fhir.newJsonParser().parseResource(ServiceRequest.class, call.body());
            assertThat(sent.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.REVOKED);
        });
    }

    @Test
    void cancellingInHisSucceedsWhenELabFailsOrDoesNotKnowTheOrder() throws Exception {
        String id = order();
        FAILURE.set(404);
        cancelInHis(id);

        String other = order();
        FAILURE.set(-1);
        cancelInHis(other);
    }

    @Test
    void cancellationInitiatedByELabIsNotEchoedBack() throws Exception {
        String id = order();
        CALLS.clear();

        mvc.perform(put("/fhir/ServiceRequest/{id}", id).with(FhirTestAuth.service())
                        .contentType(FHIR_JSON)
                        .content("{\"resourceType\":\"ServiceRequest\",\"status\":\"revoked\",\"intent\":\"order\"}"))
                .andExpect(status().isOk());

        assertThat(CALLS).isEmpty();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/lab-orders/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(jsonPath("$.status").value("cancelled"));
    }
}
