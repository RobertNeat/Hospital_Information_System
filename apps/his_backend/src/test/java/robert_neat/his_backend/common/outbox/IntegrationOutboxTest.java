package robert_neat.his_backend.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

import robert_neat.his_backend.TestcontainersConfiguration;
import robert_neat.his_backend.lab.LabOrderRepository;
import robert_neat.his_backend.prescription.PrescriptionRepository;
import robert_neat.his_backend.prescription.PrescriptionStatus;
import robert_neat.his_backend.prescription.ereceipt.EReceiptIntegration;

/**
 * Outbox + scheduler ponawiania wysylek: wpis powstaje przy nieudanym `POST` ({@code EReceiptIntegration},
 * {@code ELabIntegration}), {@link IntegrationOutboxScheduler#retryDue()} wywolywany bezposrednio (bez czekania na
 * `@Scheduled` - termin ponowienia przesuwany recznie przez {@link OutboxRepository}) ponawia go, a po
 * {@code maxAttempts} nieudanych probach wpis trafia w stan koncowy `failed`. Atrapy e-receipt/e-laboratory to
 * wbudowane serwery JDK (jak w `EReceiptFlowTest`/`ELabFlowTest`) - ten sam proces obsluguje obie integracje jednym
 * portem kazda, bo wlaczamy `his.integration.ereceipt.enabled` i `his.integration.elab.enabled` jednoczesnie.
 */
@SpringBootTest(properties = {"spring.liquibase.contexts=reference,mock", "his.integration.ereceipt.enabled=true",
        "his.integration.elab.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IntegrationOutboxTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String PATIENT_RX = "50c8f3fa-ea66-581a-9207-f9c4c7131d26";
    private static final String PATIENT_LAB = "c078186c-c437-5fa8-8a5b-a6bf8883f8bf";
    private static final String POLPRIL = "24c2f3ff-06d4-53d2-9bc5-561b36774246";

    record Call(String method, String path) {
    }

    private static final List<Call> RX_CALLS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger KEY_SEQUENCE = new AtomicInteger();
    /** 0 = normalnie (201 + klucz), >0 = kod bledu, -1 = zerwane polaczenie. */
    private static final AtomicInteger RX_FAILURE = new AtomicInteger();
    private static final AtomicReference<String> LAST_KEY = new AtomicReference<>();
    /** Hak wywolywany PO odebraniu POST-a, PRZED wyslaniem odpowiedzi - symuluje zmiane stanu "w locie" HTTP. */
    private static final AtomicReference<Runnable> ON_POST_RECEIVED = new AtomicReference<>(() -> {
    });
    private static final HttpServer FAKE_E_RECEIPT = startFakeEReceipt();

    private static final List<Call> LAB_CALLS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger LAB_FAILURE = new AtomicInteger();
    private static final HttpServer FAKE_E_LAB = startFakeELab();

    @Autowired
    MockMvc mvc;
    @Autowired
    OutboxRepository outbox;
    @Autowired
    IntegrationOutboxScheduler scheduler;
    @Autowired
    PrescriptionRepository prescriptions;
    @Autowired
    LabOrderRepository labOrders;
    @Autowired
    EReceiptIntegration eReceiptIntegration;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactionManager;
    org.springframework.transaction.support.TransactionTemplate txTemplate;

    @BeforeEach
    void setUpTxTemplate() {
        txTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    private static HttpServer startFakeEReceipt() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/fhir/MedicationRequest", exchange -> {
                RX_CALLS.add(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath()));
                exchange.getRequestBody().readAllBytes();
                int failure = RX_FAILURE.get();
                if (failure == -1) {
                    exchange.close();
                    return;
                }
                byte[] response = new byte[0];
                int code = failure;
                if (failure <= 0 && "POST".equals(exchange.getRequestMethod())) {
                    ON_POST_RECEIVED.get().run();
                    String key = String.format("%044d", KEY_SEQUENCE.incrementAndGet());
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

    private static HttpServer startFakeELab() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/fhir/ServiceRequest", exchange -> {
                LAB_CALLS.add(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath()));
                exchange.getRequestBody().readAllBytes();
                int failure = LAB_FAILURE.get();
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
    static void urls(DynamicPropertyRegistry registry) {
        registry.add("his.integration.ereceipt.base-url",
                () -> "http://127.0.0.1:" + FAKE_E_RECEIPT.getAddress().getPort() + "/fhir");
        registry.add("his.integration.elab.base-url",
                () -> "http://127.0.0.1:" + FAKE_E_LAB.getAddress().getPort() + "/fhir");
    }

    @AfterAll
    static void stopFakes() {
        FAKE_E_RECEIPT.stop(0);
        FAKE_E_LAB.stop(0);
    }

    @BeforeEach
    void reset() {
        RX_CALLS.clear();
        RX_FAILURE.set(0);
        ON_POST_RECEIVED.set(() -> {
        });
        LAB_CALLS.clear();
        LAB_FAILURE.set(0);
    }

    // --- pomocnicze ---

    private String token() throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"employeeId\":\"doctor\",\"password\":\"doctor\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return "Bearer " + JsonPath.<String>read(response, "$.accessToken");
    }

    private String issuePrescription() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String body = "{\"validFrom\":\"" + today + "\",\"validUntil\":\"" + today.plusDays(30) + "\",\"kind\":\""
                + "e_prescription" + "\",\"items\":[{\"drugId\":\"" + POLPRIL + "\",\"dosage\":{\"dose\":5,"
                + "\"doseUnit\":\"mg\",\"route\":\"oral\",\"frequency\":\"BID\",\"durationDays\":30,"
                + "\"asNeeded\":false},\"quantityPackages\":1,\"reimbursement\":\"30%\",\"substitutionAllowed\":true}]}";
        String response = mvc.perform(post("/api/v1/patients/{id}/prescriptions", PATIENT_RX)
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

    private void cancelPrescription(String id) throws Exception {
        mvc.perform(post("/api/v1/prescriptions/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("cancelled"));
    }

    private String placeLabOrder() throws Exception {
        String body = "{\"items\":[{\"testCode\":\"MORF\",\"specimenType\":\"blood\"}],\"urgency\":\"urgent\","
                + "\"fasting\":false,\"plannedCollectionAt\":\"2030-01-01T08:00:00Z\",\"clinicalInfo\":\"Kontrola.\"}";
        String response = mvc.perform(post("/api/v1/patients/{id}/lab-orders", PATIENT_LAB)
                        .header(HttpHeaders.AUTHORIZATION, token()).contentType(JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(response, "$.id");
    }

    private void cancelLabOrder(String id) throws Exception {
        mvc.perform(post("/api/v1/lab-orders/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(JSON).content("{\"reason\":\"pomylka\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("cancelled"));
    }

    private Optional<OutboxEntry> findEntry(OutboxIntegration integration, UUID entityId) {
        return outbox.findByIntegrationAndOperationAndEntityId(integration, OutboxOperation.SUBMIT, entityId);
    }

    /** Przesuwa termin ponowienia w przeszlosc, zeby scheduler podjal wpis natychmiast (bez czekania na timer). */
    private void makeDueNow(OutboxIntegration integration, UUID entityId) {
        OutboxEntry entry = findEntry(integration, entityId).orElseThrow();
        entry.forceDueNow();
        outbox.saveAndFlush(entry);
    }

    // --- e-receipt: outbox powstaje na bledzie, retry udany, race na erx_key ---

    @Test
    void failedSubmitCreatesPendingOutboxRow() throws Exception {
        RX_FAILURE.set(500);
        String id = issuePrescription();

        OutboxEntry entry = findEntry(OutboxIntegration.ERECEIPT, UUID.fromString(id)).orElseThrow();
        assertThat(entry.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(entry.getAttempts()).isEqualTo(1);
        assertThat(storedKey(id)).doesNotMatch("\\d{44}");
    }

    @Test
    void schedulerRetriesAndMarksSucceeded() throws Exception {
        RX_FAILURE.set(500);
        String id = issuePrescription();
        assertThat(storedKey(id)).doesNotMatch("\\d{44}");

        RX_FAILURE.set(0);
        makeDueNow(OutboxIntegration.ERECEIPT, UUID.fromString(id));
        scheduler.retryDue();

        OutboxEntry entry = findEntry(OutboxIntegration.ERECEIPT, UUID.fromString(id)).orElseThrow();
        assertThat(entry.getStatus()).isEqualTo(OutboxStatus.SUCCEEDED);
        assertThat(storedKey(id)).isEqualTo(LAST_KEY.get()).matches("\\d{44}");
    }

    @Test
    void maxAttemptsGivesUpWithoutFurtherCalls() throws Exception {
        RX_FAILURE.set(500);
        String id = issuePrescription();
        UUID entityId = UUID.fromString(id);

        int maxAttempts = findEntry(OutboxIntegration.ERECEIPT, entityId).orElseThrow().getMaxAttempts();
        for (int i = 1; i < maxAttempts; i++) {
            makeDueNow(OutboxIntegration.ERECEIPT, entityId);
            scheduler.retryDue();
        }

        OutboxEntry entry = findEntry(OutboxIntegration.ERECEIPT, entityId).orElseThrow();
        assertThat(entry.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(entry.getAttempts()).isEqualTo(maxAttempts);
        int callsBefore = RX_CALLS.size();

        makeDueNow(OutboxIntegration.ERECEIPT, entityId);
        scheduler.retryDue();

        // Wpis "failed" jest koncowy - kolejny przebieg schedulera go nie wybiera (findDue filtruje po PENDING).
        assertThat(RX_CALLS).hasSize(callsBefore);
        assertThat(findEntry(OutboxIntegration.ERECEIPT, entityId).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void retryDoesNotResurrectAPrescriptionCancelledBeforeFirstSubmitSucceeded() throws Exception {
        // POST nigdy nie dotarl (zerwane polaczenie) - recepta zostaje anulowana w HIS, zanim retry wystartuje.
        RX_FAILURE.set(-1);
        String id = issuePrescription();
        cancelPrescription(id);
        RX_CALLS.clear();
        RX_FAILURE.set(0);

        makeDueNow(OutboxIntegration.ERECEIPT, UUID.fromString(id));
        scheduler.retryDue();

        // Bez POST - retry widzi recepte juz anulowana (nie "open") i oznacza wpis jako superseded.
        assertThat(RX_CALLS).isEmpty();
        assertThat(findEntry(OutboxIntegration.ERECEIPT, UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.SUCCEEDED);
        assertThat(storedKey(id)).doesNotMatch("\\d{44}");
    }

    @Test
    void retryReconciliatesCancellationThatRacedTheDelayedSubmit() throws Exception {
        // Recepta jest anulowana w HIS DOKLADNIE miedzy odebraniem POST-a przez atrape a zapisem klucza w HIS
        // (ON_POST_RECEIVED - symuluje watek anulowania wyprzedzajacy watek retry tuz po wyslaniu POST, zanim
        // retry zdazyl zapisac odpowiedz). Taki PUT anulowania (gdyby probowal go wyslac rownolegly watek
        // `onCancelled`) dostalby 404 u atrapy (jeszcze nie zna recepty) - retry musi wiec sam, po udanym POST
        // i zapisie klucza, zauwazyc anulowanie i doslac PUT nowym kluczem.
        RX_FAILURE.set(-1); // POST nie dotarl przy wystawieniu - recepta zostaje "open" (klucz lokalny)
        String id = issuePrescription();
        RX_CALLS.clear();
        RX_FAILURE.set(0); // teraz retry (POST) sie powiedzie
        ON_POST_RECEIVED.set(() -> cancelPrescriptionDirectlyInDb(id));

        boolean done = eReceiptIntegration.retry(OutboxOperation.SUBMIT, UUID.fromString(id));

        assertThat(done).isTrue();
        assertThat(RX_CALLS).hasSize(2); // POST, potem PUT anulowania rekoncyliacyjny
        assertThat(RX_CALLS.get(0).method()).isEqualTo("POST");
        assertThat(RX_CALLS.get(1).method()).isEqualTo("PUT");
        assertThat(RX_CALLS.get(1).path()).isEqualTo("/fhir/MedicationRequest/" + LAST_KEY.get());
    }

    private void cancelPrescriptionDirectlyInDb(String id) {
        prescriptions.findById(UUID.fromString(id)).ifPresent(p -> {
            p.applyExternalStatus(PrescriptionStatus.CANCELLED, Instant.now(), "test");
            prescriptions.saveAndFlush(p);
        });
    }

    @Test
    void retryDoesNotOverwriteAKeyAlreadySetByAFasterConcurrentAttempt() throws Exception {
        RX_FAILURE.set(-1);
        String id = issuePrescription();
        RX_CALLS.clear();
        RX_FAILURE.set(0);

        // Pierwsza (udana) proba ustawia klucz realny.
        boolean firstDone = eReceiptIntegration.retry(OutboxOperation.SUBMIT, UUID.fromString(id));
        assertThat(firstDone).isTrue();
        String firstKey = storedKey(id);
        assertThat(firstKey).matches("\\d{44}");

        // Druga proba (np. scheduler dzialajacy na starym odczycie "staleKey" = klucz lokalny) nie powinna juz nic
        // nadpisac, bo zapisany klucz zdazyl sie zmienic - weryfikujemy przez bezposrednie wywolanie warunkowego
        // JPQL z celowo nieaktualnym "staleKey".
        Integer updated = txTemplate.execute(s -> prescriptions.updateERxKeyIfStillLocal(UUID.fromString(id),
                "NIEAKTUALNY-LOKALNY-KLUCZ...........", "00000000000000000000000000000000000000000000"));
        assertThat(updated).isZero();
        assertThat(storedKey(id)).isEqualTo(firstKey);
    }

    // --- e-laboratory: retry zwykly + retry nie wskrzesza anulowanego zlecenia ---

    @Test
    void labOrderSubmitIsRetriedAfterFailure() throws Exception {
        LAB_FAILURE.set(500);
        String id = placeLabOrder();
        assertThat(findEntry(OutboxIntegration.ELAB, UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        LAB_FAILURE.set(0);
        makeDueNow(OutboxIntegration.ELAB, UUID.fromString(id));
        scheduler.retryDue();

        assertThat(findEntry(OutboxIntegration.ELAB, UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.SUCCEEDED);
        assertThat(LAB_CALLS).anySatisfy(c -> assertThat(c.method()).isEqualTo("POST"));
    }

    @Test
    void retryDoesNotResurrectACancelledLabOrder() throws Exception {
        LAB_FAILURE.set(-1);
        String id = placeLabOrder();
        cancelLabOrder(id);
        LAB_CALLS.clear();
        LAB_FAILURE.set(0);

        makeDueNow(OutboxIntegration.ELAB, UUID.fromString(id));
        scheduler.retryDue();

        assertThat(LAB_CALLS).isEmpty();
        assertThat(findEntry(OutboxIntegration.ELAB, UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.SUCCEEDED);
    }
}
