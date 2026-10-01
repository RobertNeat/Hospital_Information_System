package robert_neat.his_backend.lab.elab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ca.uhn.fhir.context.FhirContext;
import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.lab.events.LabOrderStatusChanged;
import robert_neat.his_backend.lab.events.LabResultRecorded;

/** Endpointy FHIR `/fhir/ServiceRequest` i `/fhir/DiagnosticReport` dla e-laboratory na danych mock. */
@RecordApplicationEvents
class LabFhirApiTest extends ApiIntegrationTest {

    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");
    private static final String KEY = "test-only-service-key";

    private static final String PATIENT = "0f0db024-a3c0-56a5-916d-3acf34a052e5";
    private static final String ORD_ORDERED = "8651089d-9360-5c5d-b90c-2986ca095295"; // MORF + DDIMER
    private static final String ORD_SCHEDULED = "b6a0f537-bd2a-5dbe-b944-3786793db028"; // GLU
    private static final String ORD_IN_PROGRESS = "4762fbd9-5f6c-516a-94fb-4b12d0ac3ada"; // MORF + CRP
    private static final String ORD_COMPLETED = "c288e021-f72f-5590-b4ae-87c1ef6b6c08";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FhirContext fhir;
    @Autowired
    private ApplicationEvents events;

    private static String sr(String r4, String wire) {
        return """
                {"resourceType":"ServiceRequest","status":"%s","intent":"order",
                 "extension":[{"url":"urn:his:fhir:lab-order-status","valueString":"%s"}],
                 "subject":{"reference":"Patient/x"}}
                """.formatted(r4, wire);
    }

    private ResultActions putStatus(String id, String body) throws Exception {
        return mvc.perform(put("/fhir/ServiceRequest/{id}", id).header("X-Service-Key", KEY)
                .contentType(FHIR_JSON).content(body));
    }

    private Map<String, Object> row(String id) {
        return jdbc.queryForMap("select status, version, updated_by_id from lab_order where id = ?::uuid", id);
    }

    private String patientOf(String orderId) {
        return jdbc.queryForList("select patient_id::text from lab_order where id = ?::uuid", String.class, orderId)
                .stream().findFirst().orElse(PATIENT);
    }

    private String report(String orderId, String testCode, String status, String issued, String... observations) {
        StringBuilder contained = new StringBuilder();
        StringBuilder results = new StringBuilder();
        for (int i = 0; i < observations.length; i++) {
            contained.append(i == 0 ? "" : ",").append(observations[i].replace("$ID", "o" + i));
            results.append(i == 0 ? "" : ",").append("{\"reference\":\"#o").append(i).append("\"}");
        }
        return """
                {"resourceType":"DiagnosticReport","status":"%s",
                 "code":{"coding":[{"system":"urn:his:lab-test","code":"%s"}]},
                 "subject":{"reference":"Patient/%s"},
                 "basedOn":[{"reference":"ServiceRequest/%s"}],
                 "effectiveDateTime":"2026-10-01T08:00:00Z","issued":"%s",
                 "performer":[{"display":"Laborant Test"}],"conclusion":"komentarz",
                 "contained":[%s],"result":[%s]}
                """.formatted(status, testCode, patientOf(orderId), orderId, issued, contained, results);
    }

    private static String quantity(String analyte, String value, String flag) {
        return "{\"resourceType\":\"Observation\",\"id\":\"$ID\",\"status\":\"final\","
                + "\"code\":{\"coding\":[{\"system\":\"urn:his:lab-analyte\",\"code\":\"" + analyte + "\"}]},"
                + "\"valueQuantity\":{\"value\":" + value + ",\"unit\":\"x\"}"
                + (flag == null ? "" : ",\"interpretation\":[{\"coding\":[{\"system\":"
                        + "\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"code\":\""
                        + flag + "\"}]}]")
                + "}";
    }

    private ResultActions postReport(String body) throws Exception {
        return mvc.perform(post("/fhir/DiagnosticReport").header("X-Service-Key", KEY).contentType(FHIR_JSON)
                .content(body));
    }

    // --- autoryzacja ---

    @Test
    void requestsWithoutKeyAreUnauthorizedOperationOutcome() throws Exception {
        String json = mvc.perform(get("/fhir/ServiceRequest/" + ORD_ORDERED)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(FHIR_JSON)).andReturn().getResponse()
                .getContentAsString();
        assertThat(fhir.newJsonParser().parseResource(OperationOutcome.class, json).getIssueFirstRep().getCode()
                .toCode()).isEqualTo("security");
        mvc.perform(post("/fhir/DiagnosticReport").header("X-Service-Key", "zly").contentType(FHIR_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }

    // --- ServiceRequest: odczyt ---

    @Test
    void readReturnsServiceRequestWithItemsAndAnalyteDefinitions() throws Exception {
        String json = mvc.perform(get("/fhir/ServiceRequest/" + ORD_ORDERED).header("X-Service-Key", KEY))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ServiceRequest sr = fhir.newJsonParser().parseResource(ServiceRequest.class, json);

        assertThat(sr.getIdElement().getIdPart()).isEqualTo(ORD_ORDERED);
        assertThat(sr.getIdentifier()).extracting(i -> i.getSystem() + "=" + i.getValue())
                .contains("urn:his:lab-order-id=" + ORD_ORDERED);
        assertThat(sr.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.ACTIVE);
        assertThat(sr.getExtensionByUrl("urn:his:fhir:lab-order-status").getValue().primitiveValue())
                .isEqualTo("ordered");
        assertThat(sr.getOrderDetail()).extracting(d -> d.getCodingFirstRep().getCode())
                .containsExactlyInAnyOrder("MORF", "DDIMER");
        assertThat(sr.getOrderDetail()).allSatisfy(d -> assertThat(d.getExtensionsByUrl("urn:his:fhir:lab-analyte"))
                .isNotEmpty());
    }

    @Test
    void readUnknownOrMalformedIdIsNotFound() throws Exception {
        mvc.perform(get("/fhir/ServiceRequest/nie-uuid").header("X-Service-Key", KEY))
                .andExpect(status().isNotFound());
        mvc.perform(get("/fhir/ServiceRequest/00000000-0000-0000-0000-000000000000").header("X-Service-Key", KEY))
                .andExpect(status().isNotFound());
    }

    // --- ServiceRequest: zmiana stanu ---

    @Test
    void collectingUpdatesStatusHistoryVersionAndPublishesSystemEvent() throws Exception {
        long before = (long) row(ORD_ORDERED).get("version");
        putStatus(ORD_ORDERED, sr("active", "specimen_collected")).andExpect(status().isOk());

        Map<String, Object> after = row(ORD_ORDERED);
        assertThat(after.get("status")).isEqualTo("specimen_collected");
        assertThat((long) after.get("version")).isGreaterThan(before);
        assertThat(after.get("updated_by_id")).isNull();
        assertThat(jdbc.queryForObject("select by_id from lab_order_status_change where order_id = ?::uuid "
                + "and status = 'specimen_collected'", Object.class, ORD_ORDERED)).isNull();
        assertThat(events.stream(LabOrderStatusChanged.class)).singleElement().satisfies(e -> {
            assertThat(e.actorId()).isNull();
            assertThat(e.previousStatus()).isEqualTo(OrderStatus.ORDERED);
            assertThat(e.status()).isEqualTo(OrderStatus.SPECIMEN_COLLECTED);
        });
    }

    @Test
    void repeatingCurrentStatusIsIdempotent() throws Exception {
        long before = (long) row(ORD_IN_PROGRESS).get("version");
        putStatus(ORD_IN_PROGRESS, sr("active", "in_progress")).andExpect(status().isOk());

        assertThat((long) row(ORD_IN_PROGRESS).get("version")).isEqualTo(before);
        assertThat(events.stream(LabOrderStatusChanged.class)).isEmpty();
    }

    @Test
    void cancellingSetsCancelledWithoutActor() throws Exception {
        putStatus(ORD_SCHEDULED, sr("revoked", "cancelled")).andExpect(status().isOk());

        assertThat(row(ORD_SCHEDULED).get("status")).isEqualTo("cancelled");
        assertThat(events.stream(LabOrderStatusChanged.class)).singleElement().satisfies(e -> {
            assertThat(e.actorId()).isNull();
            assertThat(e.note()).isEqualTo("Anulowano w e-laboratory");
        });
    }

    @Test
    void statusWithoutExtensionUsesR4Status() throws Exception {
        putStatus(ORD_IN_PROGRESS, "{\"resourceType\":\"ServiceRequest\",\"status\":\"completed\",\"intent\":\"order\","
                + "\"subject\":{\"reference\":\"Patient/x\"}}").andExpect(status().isOk());
        assertThat(row(ORD_IN_PROGRESS).get("status")).isEqualTo("completed");
    }

    @Test
    void statusRulesAreEnforced() throws Exception {
        // niedozwolone przejscie (scheduled -> in_progress), stan koncowy -> 409
        putStatus(ORD_SCHEDULED, sr("active", "in_progress")).andExpect(status().isConflict());
        putStatus(ORD_COMPLETED, sr("revoked", "cancelled")).andExpect(status().isConflict());
        // 'ordered' to nie zmiana (zlecenie scheduled) -> 422
        putStatus(ORD_SCHEDULED, sr("active", "ordered")).andExpect(status().isUnprocessableEntity());
        // identyfikator zlecenia z ciala niezgodny -> 422
        putStatus(ORD_ORDERED, "{\"resourceType\":\"ServiceRequest\",\"status\":\"active\",\"intent\":\"order\","
                + "\"identifier\":[{\"system\":\"urn:his:lab-order-id\",\"value\":\"inne\"}]}")
                .andExpect(status().isUnprocessableEntity());
        // nieznany status / status R4 spoza mapowania / zly JSON -> 400, nieznane zlecenie -> 404
        putStatus(ORD_ORDERED, sr("active", "dziwny")).andExpect(status().isBadRequest());
        putStatus(ORD_ORDERED, "{\"resourceType\":\"ServiceRequest\",\"status\":\"on-hold\",\"intent\":\"order\"}")
                .andExpect(status().isBadRequest());
        putStatus(ORD_ORDERED, "nie json").andExpect(status().isBadRequest());
        putStatus("00000000-0000-0000-0000-000000000000", sr("active", "ordered"))
                .andExpect(status().isNotFound());
        assertThat(row(ORD_SCHEDULED).get("status")).isEqualTo("scheduled");
        assertThat(events.stream(LabOrderStatusChanged.class)).isEmpty();
    }

    // --- DiagnosticReport ---

    @Test
    void reportIsRecordedAsResultWithFlagsAndSystemActor() throws Exception {
        String json = postReport(report(ORD_IN_PROGRESS, "MORF", "final", "2026-10-01T09:00:00Z",
                quantity("WBC", "12.5", null), quantity("HGB", "14", "LL")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        DiagnosticReport created = fhir.newJsonParser().parseResource(DiagnosticReport.class, json);
        String resultId = created.getIdElement().getIdPart();

        assertThat(created.getStatus()).isEqualTo(DiagnosticReport.DiagnosticReportStatus.FINAL);
        assertThat(created.getContained()).hasSize(2);
        assertThat(jdbc.queryForObject("select performer_name from lab_result where id = ?::uuid", String.class,
                resultId)).isEqualTo("Laborant Test");
        assertThat(jdbc.queryForList("select analyte_code || '=' || flag from lab_observation where result_id = ?::uuid "
                + "order by analyte_code", String.class, resultId)).containsExactly("HGB=LL", "WBC=H");
        assertThat(events.stream(LabResultRecorded.class)).singleElement().satisfies(e -> {
            assertThat(e.critical()).isTrue();
            assertThat(e.actorId()).isNull();
        });
        // pozycja CRP bez wyniku: zlecenie nadal w toku
        assertThat(row(ORD_IN_PROGRESS).get("status")).isEqualTo("in_progress");

        mvc.perform(get("/fhir/DiagnosticReport/" + resultId).header("X-Service-Key", KEY))
                .andExpect(status().isOk());
    }

    @Test
    void lastFinalResultCompletesOrderAutomatically() throws Exception {
        postReport(report(ORD_IN_PROGRESS, "MORF", "final", "2026-10-01T09:00:00Z", quantity("HGB", "14", null)))
                .andExpect(status().isCreated());
        postReport(report(ORD_IN_PROGRESS, "CRP", "final", "2026-10-01T09:05:00Z", quantity("CRP", "2", null)))
                .andExpect(status().isCreated());

        assertThat(row(ORD_IN_PROGRESS).get("status")).isEqualTo("completed");
        assertThat(events.stream(LabOrderStatusChanged.class)).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(e.actorId()).isNull();
        });
    }

    @Test
    void repeatedReportIsIdempotent() throws Exception {
        String body = report(ORD_IN_PROGRESS, "MORF", "final", "2026-10-01T09:00:00Z", quantity("HGB", "14", null));
        String first = postReport(body).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        String second = postReport(body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(fhir.newJsonParser().parseResource(DiagnosticReport.class, second).getIdElement().getIdPart())
                .isEqualTo(fhir.newJsonParser().parseResource(DiagnosticReport.class, first).getIdElement()
                        .getIdPart());
        assertThat(jdbc.queryForObject("select count(*) from lab_result where order_id = ?::uuid and test_code = 'MORF'",
                Long.class, ORD_IN_PROGRESS)).isEqualTo(1L);
    }

    @Test
    void differentFinalResultForFinalisedItemIsConflictButCorrectionIsAccepted() throws Exception {
        postReport(report(ORD_IN_PROGRESS, "MORF", "final", "2026-10-01T09:00:00Z", quantity("HGB", "14", null)))
                .andExpect(status().isCreated());
        postReport(report(ORD_IN_PROGRESS, "MORF", "final", "2026-10-01T09:30:00Z", quantity("HGB", "15", null)))
                .andExpect(status().isConflict());
        postReport(report(ORD_IN_PROGRESS, "MORF", "corrected", "2026-10-01T09:40:00Z", quantity("HGB", "15", null)))
                .andExpect(status().isCreated());
    }

    @Test
    void reportForOrderWithoutCollectedSpecimenIsConflict() throws Exception {
        postReport(report(ORD_ORDERED, "MORF", "final", "2026-10-01T09:00:00Z", quantity("HGB", "14", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidReportsAreRejected() throws Exception {
        // nieznany analit, brak zlecenia, zly pacjent -> 422
        postReport(report(ORD_IN_PROGRESS, "MORF", "final", "2026-10-01T09:00:00Z", quantity("NIEMA", "1", null)))
                .andExpect(status().isUnprocessableEntity());
        postReport(report("00000000-0000-0000-0000-000000000000", "MORF", "final", "2026-10-01T09:00:00Z",
                quantity("HGB", "14", null))).andExpect(status().isUnprocessableEntity());
        postReport(report(ORD_IN_PROGRESS, "MORF", "final", "2026-10-01T09:00:00Z", quantity("HGB", "14", null))
                .replace(patientOf(ORD_IN_PROGRESS), "50c8f3fa-ea66-581a-9207-f9c4c7131d26")).andExpect(status().isUnprocessableEntity());
        // brak basedOn, status spoza mapowania, zly JSON -> 400
        postReport(report(ORD_IN_PROGRESS, "MORF", "final", "2026-10-01T09:00:00Z", quantity("HGB", "14", null))
                .replace("\"basedOn\":[{\"reference\":\"ServiceRequest/" + ORD_IN_PROGRESS + "\"}],", ""))
                .andExpect(status().isBadRequest());
        postReport(report(ORD_IN_PROGRESS, "MORF", "registered", "2026-10-01T09:00:00Z", quantity("HGB", "14", null)))
                .andExpect(status().isBadRequest());
        postReport("nie json").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from lab_result where order_id = ?::uuid and "
                + "status <> 'preliminary' and resulted_at > '2026-10-01'", Long.class, ORD_IN_PROGRESS)).isZero();
    }

    @Test
    void readUnknownReportIsNotFound() throws Exception {
        mvc.perform(get("/fhir/DiagnosticReport/00000000-0000-0000-0000-000000000000").header("X-Service-Key", KEY))
                .andExpect(status().isNotFound());
        mvc.perform(get("/fhir/DiagnosticReport/nie-uuid").header("X-Service-Key", KEY))
                .andExpect(status().isNotFound());
    }
}
