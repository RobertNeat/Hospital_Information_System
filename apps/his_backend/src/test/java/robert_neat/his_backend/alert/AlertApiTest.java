package robert_neat.his_backend.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.alert.events.AlertCreated;
import robert_neat.his_backend.catalog.ImagingModality;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.imaging.ImagingResultStatus;
import robert_neat.his_backend.imaging.events.ImagingOrderStatusChanged;
import robert_neat.his_backend.imaging.events.ImagingResultRecorded;
import robert_neat.his_backend.lab.LabResultRecordingService;
import robert_neat.his_backend.lab.ObservationFlag;
import robert_neat.his_backend.lab.RecordLabResultCommand;
import robert_neat.his_backend.lab.RecordLabResultCommand.ObservationInput;
import robert_neat.his_backend.lab.ResultStatus;
import robert_neat.his_backend.lab.events.LabOrderStatusChanged;
import robert_neat.his_backend.lab.events.LabResultRecorded;
import robert_neat.his_backend.messaging.Priority;
import robert_neat.his_backend.messaging.events.TaskAssigned;
import robert_neat.his_backend.vitals.AnomalyDirection;
import robert_neat.his_backend.vitals.AnomalySeverity;
import robert_neat.his_backend.vitals.VitalAnomaly;
import robert_neat.his_backend.catalog.VitalType;
import robert_neat.his_backend.vitals.events.VitalAnomalyDetected;

/**
 * Alerty i dashboard (API.md, par. 8/9/14) na danych mock: 6 alertow, potwierdzenia `EMP-0001` (cfc4bcf6) i
 * `EMP-0006` (efe21816). Listenery sprawdzane przez opublikowanie zdarzenia w transakcji testu oraz przez realne akcje
 * domenowe (wynik lab, zlecenie, zadanie, parametry zyciowe).
 */
@RecordApplicationEvents
class AlertApiTest extends ApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private static final String A_TROPONIN = "fbdd048e-8b29-5846-9cb1-f66a2ae200da"; // critical_result, Wisniewska
    private static final String A_POTASSIUM = "cfc4bcf6-b56a-5de8-8324-2151f2649eb0"; // critical_result, Mazur, ack EMP-0001
    private static final String A_SPO2 = "ac8c5aef-7b5e-527d-b890-46ba70d8a106"; // vital_anomaly critical, Szymanski
    private static final String A_PRESSURE = "efe21816-c2cf-5367-9702-721e6552a976"; // vital_anomaly warning, ack EMP-0006
    private static final String A_CT = "2564e205-fb02-5231-9684-501e969496ca"; // order_status info, Szymanski
    private static final String A_TASK = "42b4ded7-1fbe-534e-abf0-12c147e01d99"; // task warning, 17a3dd05

    private static final String DOC_1 = "16259545-f97c-531d-b9cd-6ba115379372"; // EMP-0001
    private static final String NURSE_6 = "625e824c-3b63-51c2-9e56-78cbfd0ff9a5"; // EMP-0006
    private static final String NURSE_8 = "570c2cc4-eafe-5082-a1ca-e47097316d3a"; // EMP-0008

    private static final String KOWALSKI = "c078186c-c437-5fa8-8a5b-a6bf8883f8bf"; // ward internal, attending EMP-0001
    private static final String WARD_INTERNAL = "25c25490-5067-5aaa-bcf5-5dc23f56588b";
    private static final String SZYMANSKI = "50c8f3fa-ea66-581a-9207-f9c4c7131d26";
    private static final String MAZUR = "55cc6e9e-6413-58bc-88b6-6342579d8413";
    private static final String WISNIEWSKA = "7466c824-06b6-57f2-8bae-2f20f77d64b6";
    private static final String ORD_LAB_ORDERED = "8651089d-9360-5c5d-b90c-2986ca095295";
    private static final String ORD_IMG_ORDERED = "ec2e14bd-4918-5bae-abb5-5fac44f71be1";
    private static final String CRP_PATIENT = "0f0db024-a3c0-56a5-916d-3acf34a052e5";

    private static final String UNKNOWN = "00000000-0000-4000-8000-000000000000";

    private static final Map<String, String> TOKENS = new HashMap<>();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEvents events;
    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private LabResultRecordingService recording;

    // --- uwierzytelnienie i uprawnienia ---

    @Test
    void everyEndpointWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/alerts")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/alerts/{id}/acknowledge", A_TROPONIN)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/dashboard/stats")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "nurse", "lab-tech", "radiologist", "admin"})
    void rolesWithAlertReadMayList(String login) throws Exception {
        as(login, get("/api/v1/alerts")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pharmacist", "registrar"})
    void rolesWithoutAlertReadGet403(String login) throws Exception {
        as(login, get("/api/v1/alerts")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as(login, post("/api/v1/alerts/{id}/acknowledge", A_TROPONIN)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lab-tech", "radiologist", "admin"})
    void readOnlyRolesCannotAcknowledge(String login) throws Exception {
        as(login, post("/api/v1/alerts/{id}/acknowledge", A_TROPONIN)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from alert_acknowledgement", Integer.class)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "nurse", "lab-tech", "radiologist", "pharmacist", "registrar", "admin"})
    void everyRoleMayReadDashboard(String login) throws Exception {
        as(login, get("/api/v1/dashboard/stats")).andExpect(status().isOk());
    }

    // --- dane mock i kontrakt JSON ---

    @Test
    void listReturnsSixMockAlertsNewestFirstWithViewerProjection() throws Exception {
        as("EMP-0001", get("/api/v1/alerts")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].id",
                        contains(A_TASK, A_CT, A_SPO2, A_TROPONIN, A_POTASSIUM, A_PRESSURE)))
                .andExpect(jsonPath("$[*].acknowledged", contains(false, false, false, false, true, false)));
        as("EMP-0006", get("/api/v1/alerts")).andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].acknowledged", contains(false, false, false, false, false, true)));
        // konto bez potwierdzen widzi wszystko jako niepotwierdzone
        as("doctor", get("/api/v1/alerts")).andExpect(jsonPath("$[*].acknowledged",
                contains(false, false, false, false, false, false)));
    }

    @Test
    void jsonShapeMatchesContract() throws Exception {
        String json = as("EMP-0001", get("/api/v1/alerts")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        // krytyczny wynik z celem i pacjentem celu
        Map<String, Object> troponin = byId(json, A_TROPONIN);
        assertThat(troponin).containsEntry("type", "critical_result").containsEntry("severity", "critical")
                .containsEntry("patientId", WISNIEWSKA).containsEntry("acknowledged", false)
                .doesNotContainKeys("acknowledgedById", "acknowledgedAt", "link");
        assertThat(troponin.get("message")).asString().contains("troponiny");
        assertThat(Instant.parse((String) troponin.get("createdAt"))).isBefore(Instant.now());
        assertThat(troponin.get("target")).isEqualTo(Map.of("kind", "lab_result",
                "id", "f581ba19-b12b-56a2-bc2d-f6f1b94b7bd2", "patientId", WISNIEWSKA));
        // cel bez patientId (patient_vitals: id = pacjent)
        assertThat(byId(json, A_SPO2).get("target"))
                .isEqualTo(Map.of("kind", "patient_vitals", "id", SZYMANSKI));
        // brak celu: pole `target` pomijane
        assertThat(byId(json, A_PRESSURE)).doesNotContainKey("target").containsEntry("severity", "warning")
                .containsEntry("acknowledged", false).containsEntry("type", "vital_anomaly");
        assertThat(byId(json, A_TASK)).doesNotContainKey("target").containsEntry("type", "task");
        assertThat(byId(json, A_CT)).containsEntry("type", "order_status").containsEntry("severity", "info");
        // potwierdzone przez biezacego: acknowledgedById/At
        Map<String, Object> potassium = byId(json, A_POTASSIUM);
        assertThat(potassium).containsEntry("acknowledged", true).containsEntry("acknowledgedById", DOC_1);
        assertThat(Instant.parse((String) potassium.get("acknowledgedAt"))).isBefore(Instant.now());
        assertThat(jdbc.queryForObject("select acknowledged_at is not null from alert_acknowledgement"
                + " where alert_id = ?::uuid and staff_id = ?::uuid", Boolean.class, A_POTASSIUM, DOC_1)).isTrue();
    }

    @Test
    void filtersByPatientAndAcknowledgedRelativeToViewer() throws Exception {
        as("EMP-0001", get("/api/v1/alerts?patientId=" + MAZUR)).andExpect(jsonPath("$[*].id",
                contains(A_POTASSIUM, A_PRESSURE)));
        as("EMP-0001", get("/api/v1/alerts?patientId=" + MAZUR + "&acknowledged=false"))
                .andExpect(jsonPath("$[*].id", contains(A_PRESSURE)));
        as("EMP-0001", get("/api/v1/alerts?patientId=" + MAZUR + "&acknowledged=true"))
                .andExpect(jsonPath("$[*].id", contains(A_POTASSIUM)));
        as("EMP-0006", get("/api/v1/alerts?acknowledged=true")).andExpect(jsonPath("$[*].id",
                contains(A_PRESSURE)));
        as("EMP-0006", get("/api/v1/alerts?acknowledged=false")).andExpect(jsonPath("$", hasSize(5)));
        as("doctor", get("/api/v1/alerts?acknowledged=true")).andExpect(jsonPath("$", hasSize(0)));
        as("EMP-0001", get("/api/v1/alerts?patientId=" + UNKNOWN)).andExpect(jsonPath("$", hasSize(0)));
        as("EMP-0001", get("/api/v1/alerts?patientId=" + KOWALSKI)).andExpect(jsonPath("$", hasSize(0)));
    }

    // --- potwierdzenie ---

    @Test
    void acknowledgeIsIdempotentAndPerUser() throws Exception {
        String first = as("EMP-0001", post("/api/v1/alerts/{id}/acknowledge", A_SPO2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(A_SPO2)).andExpect(jsonPath("$.acknowledged").value(true))
                .andExpect(jsonPath("$.acknowledgedById").value(DOC_1))
                .andExpect(jsonPath("$.target.kind").value("patient_vitals"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String at = JsonPath.read(first, "$.acknowledgedAt");
        // ponowne potwierdzenie: ten sam kto/kiedy, jeden wiersz
        as("EMP-0001", post("/api/v1/alerts/{id}/acknowledge", A_SPO2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledgedAt").value(at));
        assertThat(ackRows(A_SPO2)).isEqualTo(1);
        // projekcja drugiego konta bez zmian
        as("EMP-0006", get("/api/v1/alerts?patientId=" + SZYMANSKI)).andExpect(jsonPath("$[0].acknowledged")
                .value(false)).andExpect(jsonPath("$[0].acknowledgedById").doesNotExist());
        as("EMP-0001", get("/api/v1/alerts?acknowledged=false")).andExpect(jsonPath("$", hasSize(4)));
        as("EMP-0006", get("/api/v1/alerts?acknowledged=false")).andExpect(jsonPath("$", hasSize(5)));
        // drugie konto potwierdza niezaleznie
        as("EMP-0006", post("/api/v1/alerts/{id}/acknowledge", A_SPO2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledgedById").value(NURSE_6));
        assertThat(ackRows(A_SPO2)).isEqualTo(2);
        as("EMP-0001", get("/api/v1/alerts?patientId=" + SZYMANSKI + "&acknowledged=true"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void acknowledgeOfAlreadyAcknowledgedMockAlertKeepsOriginalTime() throws Exception {
        Instant original = jdbc.queryForObject("select acknowledged_at from alert_acknowledgement"
                + " where alert_id = ?::uuid and staff_id = ?::uuid", java.sql.Timestamp.class, A_POTASSIUM, DOC_1)
                .toInstant();
        String body = as("EMP-0001", post("/api/v1/alerts/{id}/acknowledge", A_POTASSIUM)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(Instant.parse(JsonPath.read(body, "$.acknowledgedAt"))).isEqualTo(original);
        assertThat(ackRows(A_POTASSIUM)).isEqualTo(1);
    }

    @Test
    void acknowledgeUnknownOrMalformedIdIs404() throws Exception {
        as("EMP-0001", post("/api/v1/alerts/{id}/acknowledge", UNKNOWN)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        as("EMP-0001", post("/api/v1/alerts/{id}/acknowledge", "nie-uuid")).andExpect(status().isNotFound());
    }

    // --- listenery: zdarzenia -> alerty ---

    @Test
    void criticalLabResultCreatesCriticalResultAlert() throws Exception {
        UUID resultId = UUID.randomUUID();
        publisher.publishEvent(new LabResultRecorded(resultId, uuid(KOWALSKI), null, uuid(NURSE_8), "ELEK",
                ResultStatus.FINAL, true, List.of("K", "NA"), Instant.now(), uuid(DOC_1)));
        Map<String, Object> row = alertRow(resultId);
        assertThat(row).containsEntry("type", "critical_result").containsEntry("severity", "critical")
                .containsEntry("target_kind", "lab_result").containsEntry("patient_id", uuid(KOWALSKI))
                .containsEntry("target_patient_id", uuid(KOWALSKI));
        assertThat((String) row.get("message")).contains("ELEK").contains("K, NA").contains("pacjent ");
        // adresaci: zlecajacy + lekarz prowadzacy; oddzial z aktywnego przyjecia
        assertThat(events.stream(AlertCreated.class)).singleElement().satisfies(e -> {
            assertThat(e.alertId()).isEqualTo(row.get("id"));
            assertThat(e.wardId()).isEqualTo(uuid(WARD_INTERNAL));
            assertThat(e.recipientIds()).containsExactly(uuid(NURSE_8), uuid(DOC_1));
        });
        // nowy alert jest niepotwierdzony dla kazdego
        as("EMP-0001", get("/api/v1/alerts?acknowledged=false")).andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].id").value(row.get("id").toString()))
                .andExpect(jsonPath("$[0].target.id").value(resultId.toString()));
    }

    // P8: kod analitu rowny kodowi badania (np. jednoanalitowe TROP) nie moze sie dublowac w tresci alertu.
    @Test
    void criticalLabResultWithAnalyteCodeEqualToTestCodeDoesNotDuplicateCode() throws Exception {
        UUID resultId = UUID.randomUUID();
        publisher.publishEvent(new LabResultRecorded(resultId, uuid(KOWALSKI), null, uuid(NURSE_8), "TROP",
                ResultStatus.FINAL, true, List.of("TROP"), Instant.now(), uuid(DOC_1)));
        Map<String, Object> row = alertRow(resultId);
        assertThat((String) row.get("message")).contains("laboratoryjnego TROP - ")
                .doesNotContain("TROP (TROP)");
    }

    @Test
    void nonCriticalLabAndImagingResultsCreateNoAlert() {
        publisher.publishEvent(new LabResultRecorded(UUID.randomUUID(), uuid(KOWALSKI), null, null, "MORF",
                ResultStatus.FINAL, false, List.of(), Instant.now(), null));
        publisher.publishEvent(new ImagingResultRecorded(UUID.randomUUID(), uuid(KOWALSKI), null, null,
                ImagingModality.RTG, ImagingResultStatus.FINAL, false, Instant.now(), null));
        assertThat(alertCount()).isEqualTo(6);
        assertThat(events.stream(AlertCreated.class)).isEmpty();
    }

    @Test
    void criticalImagingResultCreatesCriticalResultAlertWithoutOrderer() {
        UUID resultId = UUID.randomUUID();
        publisher.publishEvent(new ImagingResultRecorded(resultId, uuid(SZYMANSKI), null, null, ImagingModality.CT,
                ImagingResultStatus.FINAL, true, Instant.now(), uuid(DOC_1)));
        Map<String, Object> row = alertRow(resultId);
        assertThat(row).containsEntry("type", "critical_result").containsEntry("severity", "critical")
                .containsEntry("target_kind", "imaging_result");
        assertThat((String) row.get("message")).contains("CT");
        // zlecajacy nieznany: adresatem jest tylko lekarz prowadzacy (fdf3ef3c)
        assertThat(events.stream(AlertCreated.class)).singleElement().satisfies(e -> assertThat(e.recipientIds())
                .containsExactly(uuid("fdf3ef3c-c118-5896-aa53-c2eff5aa7a21")));
    }

    @Test
    void criticalVitalAnomalyCreatesVitalAnomalyAlert() {
        Instant now = Instant.now();
        VitalAnomaly anomaly = new VitalAnomaly(VitalType.SPO2, new BigDecimal("85"), AnomalySeverity.CRITICAL,
                AnomalyDirection.LOW, "Saturacja SpO₂: wartość krytycznie niska (85 %).", now);
        publisher.publishEvent(new VitalAnomalyDetected(uuid(KOWALSKI), UUID.randomUUID(), List.of(anomaly), now,
                uuid(NURSE_6)));
        Map<String, Object> row = alertRows("target_kind = 'patient_vitals' and target_id = ?::uuid", KOWALSKI)
                .getFirst();
        assertThat(row).containsEntry("type", "vital_anomaly").containsEntry("severity", "critical")
                .containsEntry("patient_id", uuid(KOWALSKI)).containsEntry("target_patient_id", null);
        assertThat((String) row.get("message")).contains("krytycznie niska");
        assertThat(events.stream(AlertCreated.class)).singleElement().satisfies(e -> {
            assertThat(e.wardId()).isEqualTo(uuid(WARD_INTERNAL));
            assertThat(e.recipientIds()).containsExactly(uuid(DOC_1));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"completed", "cancelled"})
    void labOrderTerminalStatusCreatesOrderStatusAlert(String wire) {
        OrderStatus status = OrderStatus.valueOf(wire.toUpperCase());
        UUID orderId = UUID.randomUUID();
        publisher.publishEvent(new LabOrderStatusChanged(orderId, uuid(KOWALSKI), uuid(DOC_1),
                OrderStatus.IN_PROGRESS, status, Instant.now(), uuid(NURSE_6), "Błąd zlecenia"));
        Map<String, Object> row = alertRow(orderId);
        assertThat(row).containsEntry("type", "order_status").containsEntry("severity", "info")
                .containsEntry("target_kind", "lab_order").containsEntry("patient_id", uuid(KOWALSKI));
        assertThat((String) row.get("message")).contains("laboratoryjne")
                .contains(status == OrderStatus.CANCELLED ? "Anulowano" : "Zakończono")
                .contains(status == OrderStatus.CANCELLED ? "Błąd zlecenia" : "pacjent");
        // adresat: tylko zlecajacy (bez lekarza prowadzacego)
        assertThat(events.stream(AlertCreated.class)).singleElement()
                .satisfies(e -> assertThat(e.recipientIds()).containsExactly(uuid(DOC_1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"completed", "cancelled"})
    void imagingOrderTerminalStatusCreatesOrderStatusAlert(String wire) {
        OrderStatus status = OrderStatus.valueOf(wire.toUpperCase());
        UUID orderId = UUID.randomUUID();
        publisher.publishEvent(new ImagingOrderStatusChanged(orderId, uuid(SZYMANSKI), uuid(DOC_1),
                OrderStatus.IN_PROGRESS, status, Instant.now(), null, null));
        Map<String, Object> row = alertRow(orderId);
        assertThat(row).containsEntry("type", "order_status").containsEntry("severity", "info")
                .containsEntry("target_kind", "imaging_order").containsEntry("target_patient_id", uuid(SZYMANSKI));
        assertThat((String) row.get("message")).contains("obrazowe");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ordered", "scheduled", "specimen_collected", "in_progress"})
    void nonTerminalOrderStatusCreatesNoAlert(String wire) {
        OrderStatus status = OrderStatus.valueOf(wire.toUpperCase());
        publisher.publishEvent(new LabOrderStatusChanged(UUID.randomUUID(), uuid(KOWALSKI), uuid(DOC_1),
                OrderStatus.ORDERED, status, Instant.now(), uuid(NURSE_6), null));
        publisher.publishEvent(new ImagingOrderStatusChanged(UUID.randomUUID(), uuid(KOWALSKI), uuid(DOC_1),
                OrderStatus.ORDERED, status, Instant.now(), uuid(NURSE_6), null));
        assertThat(alertCount()).isEqualTo(6);
    }

    @Test
    void taskAssignedCreatesTaskAlertWithSeverityFromPriority() {
        for (Priority p : Priority.values()) {
            publisher.publishEvent(new TaskAssigned(UUID.randomUUID(), uuid(NURSE_8), uuid(DOC_1), uuid(KOWALSKI),
                    "Zadanie " + p.wire(), p, null, Instant.now()));
        }
        publisher.publishEvent(new TaskAssigned(UUID.randomUUID(), uuid(NURSE_8), uuid(DOC_1), null, "Bez pacjenta",
                Priority.NORMAL, null, Instant.now()));
        List<Map<String, Object>> rows = alertRows("type = 'task' and created_at > now() - interval '1 minute'");
        assertThat(rows).hasSize(4).allSatisfy(r -> assertThat(r).containsEntry("target_kind", "task"));
        assertThat(rows).filteredOn(r -> r.get("message").toString().contains("Zadanie normal"))
                .singleElement().satisfies(r -> assertThat(r).containsEntry("severity", "info"));
        assertThat(rows).filteredOn(r -> r.get("message").toString().contains("Zadanie high"))
                .singleElement().satisfies(r -> assertThat(r).containsEntry("severity", "warning"));
        assertThat(rows).filteredOn(r -> r.get("message").toString().contains("Zadanie critical"))
                .singleElement().satisfies(r -> assertThat(r).containsEntry("severity", "warning")
                        .containsEntry("patient_id", uuid(KOWALSKI)));
        assertThat(rows).filteredOn(r -> r.get("message").toString().contains("Bez pacjenta"))
                .singleElement().satisfies(r -> assertThat(r).containsEntry("patient_id", null)
                        .containsEntry("target_patient_id", null));
        // adresat: osoba przypisana, bez lekarza prowadzacego
        assertThat(events.stream(AlertCreated.class)).allSatisfy(e -> assertThat(e.recipientIds())
                .containsExactly(uuid(NURSE_8)));
    }

    // --- listenery: realne akcje domenowe ---

    @Test
    void recordingCriticalLabResultCreatesAlert() {
        Instant now = Instant.now();
        var result = recording.recordResult(new RecordLabResultCommand(uuid(CRP_PATIENT), null, null, "CRP",
                now.minus(3, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS), ResultStatus.FINAL, "lab", null,
                List.of(ObservationInput.numeric("CRP", new BigDecimal("400"), ObservationFlag.HH))));
        Map<String, Object> row = alertRow(result.id());
        assertThat(row).containsEntry("type", "critical_result").containsEntry("target_kind", "lab_result")
                .containsEntry("severity", "critical");
        assertThat((String) row.get("message")).contains("CRP").doesNotContain("CRP (CRP)");
    }

    @Test
    void postingCriticalVitalsCreatesAlertAtomicallyWithReading() throws Exception {
        as("nurse", post("/api/v1/patients/{id}/vitals", KOWALSKI).contentType(JSON)
                .content("{\"context\":\"ward_round\",\"systolic\":75,\"spo2\":85}")).andExpect(status().isCreated());
        as("EMP-0001", get("/api/v1/alerts?patientId=" + KOWALSKI)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("vital_anomaly"))
                .andExpect(jsonPath("$[0].severity").value("critical"))
                .andExpect(jsonPath("$[0].target.kind").value("patient_vitals"))
                .andExpect(jsonPath("$[0].target.id").value(KOWALSKI))
                .andExpect(jsonPath("$[0].target.patientId").doesNotExist());
    }

    @Test
    void warningOnlyVitalsCreateNoAlert() throws Exception {
        as("nurse", post("/api/v1/patients/{id}/vitals", KOWALSKI).contentType(JSON)
                .content("{\"context\":\"ward_round\",\"heartRate\":105}")).andExpect(status().isCreated());
        as("EMP-0001", get("/api/v1/alerts?patientId=" + KOWALSKI)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void cancellingLabAndImagingOrdersCreatesOrderStatusAlerts() throws Exception {
        as("doctor", post("/api/v1/lab-orders/{id}/cancel", ORD_LAB_ORDERED).contentType(JSON)
                .content("{\"reason\":\"Omyłka\"}")).andExpect(status().isOk());
        as("doctor", post("/api/v1/imaging-orders/{id}/cancel", ORD_IMG_ORDERED).contentType(JSON)
                .content("{\"reason\":\"Omyłka\"}")).andExpect(status().isOk());
        assertThat(alertRow(uuid(ORD_LAB_ORDERED))).containsEntry("type", "order_status")
                .containsEntry("target_kind", "lab_order");
        assertThat(alertRow(uuid(ORD_IMG_ORDERED))).containsEntry("target_kind", "imaging_order")
                .containsEntry("severity", "info");
    }

    @Test
    void creatingTaskCreatesTaskAlertForAssignee() throws Exception {
        String task = as("EMP-0001", post("/api/v1/tasks").contentType(JSON).content(
                "{\"title\":\"Zmierzyć ciśnienie\",\"patientId\":\"" + KOWALSKI + "\",\"assignedToId\":\"" + NURSE_6
                        + "\",\"priority\":\"high\"}")).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String taskId = JsonPath.read(task, "$.id");
        as("EMP-0006", get("/api/v1/alerts?patientId=" + KOWALSKI)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("task")).andExpect(jsonPath("$[0].severity").value("warning"))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("Zmierzyć ciśnienie")))
                .andExpect(jsonPath("$[0].target.kind").value("task")).andExpect(jsonPath("$[0].target.id")
                        .value(taskId))
                .andExpect(jsonPath("$[0].target.patientId").value(KOWALSKI))
                .andExpect(jsonPath("$[0].acknowledgedAt").doesNotExist());
    }

    // --- dashboard ---

    @Test
    void dashboardStatsMatchSqlOracle() throws Exception {
        for (String login : List.of("EMP-0001", "EMP-0006", "doctor", "nurse")) {
            String staffId = jdbc.queryForObject("select staff_id::text from user_account where employee_id = ?",
                    String.class, login);
            as(login, get("/api/v1/dashboard/stats")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.admittedPatients").value(count(
                            "select count(*) from patient where status = 'admitted'")))
                    .andExpect(jsonPath("$.newResults").value(count(
                            "select count(*) from lab_result where reviewed_at is null")))
                    .andExpect(jsonPath("$.criticalAlerts").value(count(
                            "select count(*) from clinical_alert a where a.severity = 'critical' and not exists"
                                    + " (select 1 from alert_acknowledgement k where k.alert_id = a.id"
                                    + " and k.staff_id = ?::uuid)", staffId)))
                    .andExpect(jsonPath("$.openTasks").value(count(
                            "select count(*) from team_task where status = 'open' and assigned_to_id = ?::uuid",
                            staffId)))
                    .andExpect(jsonPath("$.pendingOrders").value(
                            count("select count(*) from lab_order where status in"
                                    + " ('ordered','scheduled','specimen_collected','in_progress')")
                                    + count("select count(*) from imaging_order where status in"
                                            + " ('ordered','scheduled','specimen_collected','in_progress')")))
                    .andExpect(jsonPath("$.vitalsAnomalies").isNumber());
        }
    }

    @Test
    void dashboardStatsReflectMockScenario() throws Exception {
        // krytyczne alerty mock: troponina, potas (ack EMP-0001), SpO2 -> 2 dla EMP-0001, 3 dla EMP-0006
        as("EMP-0001", get("/api/v1/dashboard/stats")).andExpect(jsonPath("$.criticalAlerts").value(2));
        as("EMP-0006", get("/api/v1/dashboard/stats")).andExpect(jsonPath("$.criticalAlerts").value(3));
        // zadania `open` mock: Clexane (EMP-0008) i wozek (EMP-0009)
        as("EMP-0008", get("/api/v1/dashboard/stats")).andExpect(jsonPath("$.openTasks").value(1));
        as("EMP-0006", get("/api/v1/dashboard/stats")).andExpect(jsonPath("$.openTasks").value(0));
        // anomalie parametrow zyciowych: ta sama projekcja co `ward-overview` (Szymanski, Kwiatkowski)
        String overview = as("doctor", get("/api/v1/vitals/ward-overview")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        List<?> withAnomalies = JsonPath.read(overview, "$[?(@.anomalies.length() > 0)]");
        assertThat(withAnomalies).hasSize(2);
        as("doctor", get("/api/v1/dashboard/stats")).andExpect(jsonPath("$.vitalsAnomalies").value(2));
    }

    @Test
    void dashboardAndAcknowledgeInteract() throws Exception {
        as("EMP-0001", post("/api/v1/alerts/{id}/acknowledge", A_SPO2)).andExpect(status().isOk());
        as("EMP-0001", get("/api/v1/dashboard/stats")).andExpect(jsonPath("$.criticalAlerts").value(1));
        // drugie konto nie zmienia licznika
        as("EMP-0006", get("/api/v1/dashboard/stats")).andExpect(jsonPath("$.criticalAlerts").value(3));
        // nowy alert krytyczny podnosi licznik wszystkim
        publisher.publishEvent(new LabResultRecorded(UUID.randomUUID(), uuid(WISNIEWSKA), null, null, "TROP",
                ResultStatus.FINAL, true, List.of("TROPI"), Instant.now(), null));
        as("EMP-0001", get("/api/v1/dashboard/stats")).andExpect(jsonPath("$.criticalAlerts").value(2));
        as("EMP-0006", get("/api/v1/dashboard/stats")).andExpect(jsonPath("$.criticalAlerts").value(4));
    }

    // --- pomocnicze ---

    private static UUID uuid(String s) {
        return UUID.fromString(s);
    }

    private int ackRows(String alertId) {
        return jdbc.queryForObject("select count(*) from alert_acknowledgement where alert_id = ?::uuid",
                Integer.class, alertId);
    }

    private int alertCount() {
        return jdbc.queryForObject("select count(*) from clinical_alert", Integer.class);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private Map<String, Object> alertRow(UUID targetId) {
        List<Map<String, Object>> rows = alertRows("target_id = ?::uuid", targetId.toString());
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private List<Map<String, Object>> alertRows(String where, Object... args) {
        return jdbc.queryForList("select * from clinical_alert where " + where + " order by created_at", args);
    }

    private static Map<String, Object> byId(String json, String id) {
        List<Map<String, Object>> found = JsonPath.read(json, "$[?(@.id=='" + id + "')]");
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    private ResultActions as(String login, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(login)));
    }

    /** Pracownicy mock (`EMP-xxxx`): haslo `HisDemo2026!`; konta demo: login = haslo. */
    private String bearer(String login) throws Exception {
        String token = TOKENS.get(login);
        if (token == null) {
            String password = login.startsWith("EMP-") ? "HisDemo2026!" : login;
            String response = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                    .content("{\"employeeId\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            token = JsonPath.read(response, "$.accessToken");
            TOKENS.put(login, token);
        }
        return "Bearer " + token;
    }
}
