package robert_neat.his_backend.imaging.eimg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ca.uhn.fhir.context.FhirContext;
import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.common.fhir.FhirTestAuth;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.imaging.events.ImagingOrderStatusChanged;
import robert_neat.his_backend.imaging.events.ImagingResultRecorded;

/** Endpointy FHIR `/fhir/ServiceRequest` i `/fhir/DiagnosticReport` dla e-imaging na danych mock. */
@RecordApplicationEvents
class ImagingFhirApiTest extends ApiIntegrationTest {

    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

    private static final String ORD_RTG = "ec2e14bd-4918-5bae-abb5-5fac44f71be1"; // ordered, RTG-KOL, right
    private static final String RTG_PATIENT = "7e25abc6-1c68-5922-8f9b-e5a8d6eeb5c9";
    private static final String ORD_USG_IP = "3deb7e2e-6305-5631-8992-75248b8d0d3a"; // in_progress, USG-JB, stat
    private static final String USG_PATIENT = "0f0db024-a3c0-56a5-916d-3acf34a052e5";
    private static final String ORD_CT = "92eb9648-eeb5-59b3-8307-b3c592976d73"; // completed
    private static final String LAB_ORD = "8651089d-9360-5c5d-b90c-2986ca095295"; // zlecenie laboratoryjne (ordered)

    private static final String SAFETY_OK = "{\"pregnancy\":\"no\",\"pacemakerOrImplant\":false,\"metalFragments\":false,"
            + "\"contrastAllergy\":false,\"claustrophobia\":false,\"confirmed\":true}";

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
                 "extension":[{"url":"urn:his:fhir:imaging-order-status","valueString":"%s"}],
                 "subject":{"reference":"Patient/x"}}
                """.formatted(r4, wire);
    }

    private ResultActions putStatus(String id, String body) throws Exception {
        return mvc.perform(put("/fhir/ServiceRequest/{id}", id).with(FhirTestAuth.service())
                .contentType(FHIR_JSON).content(body));
    }

    private Map<String, Object> row(String id) {
        return jdbc.queryForMap("select status, version, updated_by_id from imaging_order where id = ?::uuid", id);
    }

    private static String report(String orderId, String patientId, String examCode, String status, String performed,
            String reported, String findings, String conclusion, boolean critical) {
        return """
                {"resourceType":"DiagnosticReport","status":"%s",
                 "code":{"coding":[{"system":"urn:his:imaging-exam","code":"%s"}]},
                 "subject":{"reference":"Patient/%s"},
                 "basedOn":[{"reference":"ServiceRequest/%s"}],
                 "effectiveDateTime":"%s","issued":"%s",
                 "performer":[{"display":"Radiolog Test"}],"conclusion":"%s",
                 "extension":[{"url":"urn:his:fhir:imaging-findings","valueString":"%s"},
                              {"url":"urn:his:fhir:imaging-critical","valueBoolean":%b}]}
                """.formatted(status, examCode, patientId, orderId, performed, reported, conclusion, findings,
                critical);
    }

    private static String usgReport(String status, String reported) {
        return report(ORD_USG_IP, USG_PATIENT, "USG-JB", status, "2026-10-01T08:00:00Z", reported,
                "Watroba prawidlowa.", "Bez zmian ogniskowych.", false);
    }

    private ResultActions postReport(String body) throws Exception {
        return mvc.perform(post("/fhir/DiagnosticReport").with(FhirTestAuth.service()).contentType(FHIR_JSON)
                .content(body));
    }

    private String bearer() throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"doctor\",\"password\":\"doctor\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return "Bearer " + JsonPath.<String>read(response, "$.accessToken");
    }

    // --- autoryzacja i routing ---

    @Test
    void requestsWithoutCertificateAreUnauthorizedOperationOutcome() throws Exception {
        String json = mvc.perform(get("/fhir/ServiceRequest/" + ORD_RTG)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(FHIR_JSON)).andReturn().getResponse()
                .getContentAsString();
        assertThat(fhir.newJsonParser().parseResource(OperationOutcome.class, json).getIssueFirstRep().getCode()
                .toCode()).isEqualTo("security");
        mvc.perform(post("/fhir/DiagnosticReport").with(FhirTestAuth.intruder()).contentType(FHIR_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void sharedPathsRouteToTheModuleOwningTheOrder() throws Exception {
        String lab = mvc.perform(get("/fhir/ServiceRequest/" + LAB_ORD).with(FhirTestAuth.service()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(lab).contains("urn:his:lab-order-id").doesNotContain("urn:his:imaging-order-id");
        String imaging = mvc.perform(get("/fhir/ServiceRequest/" + ORD_RTG).with(FhirTestAuth.service()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(imaging).contains("urn:his:imaging-order-id").doesNotContain("urn:his:lab-order-id");
    }

    // --- ServiceRequest: odczyt ---

    @Test
    void readReturnsServiceRequestWithExamModalityLateralityAndContrast() throws Exception {
        String json = mvc.perform(get("/fhir/ServiceRequest/" + ORD_RTG).with(FhirTestAuth.service()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ServiceRequest sr = fhir.newJsonParser().parseResource(ServiceRequest.class, json);

        assertThat(sr.getIdElement().getIdPart()).isEqualTo(ORD_RTG);
        assertThat(sr.getIdentifier()).extracting(i -> i.getSystem() + "=" + i.getValue())
                .contains("urn:his:imaging-order-id=" + ORD_RTG);
        assertThat(sr.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.ACTIVE);
        assertThat(sr.getExtensionByUrl("urn:his:fhir:imaging-order-status").getValue().primitiveValue())
                .isEqualTo("ordered");
        assertThat(sr.getPriority()).isEqualTo(ServiceRequest.ServiceRequestPriority.ROUTINE);
        assertThat(sr.getSubject().getReference()).isEqualTo("Patient/" + RTG_PATIENT);
        assertThat(sr.getCode().getCodingFirstRep().getSystem()).isEqualTo("urn:his:imaging-exam");
        assertThat(sr.getCode().getCodingFirstRep().getCode()).isEqualTo("RTG-KOL");
        assertThat(sr.getOrderDetailFirstRep().getCoding()).extracting(c -> c.getSystem() + "=" + c.getCode())
                .containsExactly("urn:his:imaging-modality=RTG", "urn:his:imaging-laterality=right");
        assertThat(sr.getBodySiteFirstRep().getText()).isEqualTo("Staw kolanowy");
        assertThat(((BooleanType) sr.getExtensionByUrl("urn:his:fhir:imaging-contrast").getValue()).booleanValue())
                .isFalse();
        assertThat(sr.getReasonCodeFirstRep().getText()).contains("kolanowego");
        assertThat(sr.hasOccurrence()).isFalse();
    }

    @Test
    void readOfOrderWithSlotCarriesTermAndSlot() throws Exception {
        String slot = freeSlot("RTG");
        String id = createOrderWithSlot(slot);
        ServiceRequest sr = fhir.newJsonParser().parseResource(ServiceRequest.class,
                mvc.perform(get("/fhir/ServiceRequest/" + id).with(FhirTestAuth.service()))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(sr.getExtensionByUrl("urn:his:fhir:imaging-order-status").getValue().primitiveValue())
                .isEqualTo("scheduled");
        assertThat(sr.getExtensionByUrl("urn:his:fhir:imaging-slot").getValue().primitiveValue()).isEqualTo(slot);
        assertThat(sr.hasOccurrenceDateTimeType()).isTrue();
    }

    @Test
    void readUnknownOrMalformedIdIsNotFound() throws Exception {
        mvc.perform(get("/fhir/ServiceRequest/nie-uuid").with(FhirTestAuth.service()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/fhir/ServiceRequest/00000000-0000-0000-0000-000000000000").with(FhirTestAuth.service()))
                .andExpect(status().isNotFound());
    }

    // --- ServiceRequest: zmiana stanu ---

    @Test
    void schedulingUpdatesStatusHistoryVersionAndPublishesSystemEvent() throws Exception {
        long before = (long) row(ORD_RTG).get("version");
        putStatus(ORD_RTG, sr("active", "scheduled")).andExpect(status().isOk());

        Map<String, Object> after = row(ORD_RTG);
        assertThat(after.get("status")).isEqualTo("scheduled");
        assertThat((long) after.get("version")).isGreaterThan(before);
        assertThat(after.get("updated_by_id")).isNull();
        assertThat(jdbc.queryForObject("select by_id from imaging_order_status_change where order_id = ?::uuid "
                + "and status = 'scheduled'", Object.class, ORD_RTG)).isNull();
        assertThat(events.stream(ImagingOrderStatusChanged.class)).singleElement().satisfies(e -> {
            assertThat(e.actorId()).isNull();
            assertThat(e.previousStatus()).isEqualTo(OrderStatus.ORDERED);
            assertThat(e.status()).isEqualTo(OrderStatus.SCHEDULED);
        });
    }

    @Test
    void repeatingCurrentStatusIsIdempotent() throws Exception {
        long before = (long) row(ORD_USG_IP).get("version");
        putStatus(ORD_USG_IP, sr("active", "in_progress")).andExpect(status().isOk());

        assertThat((long) row(ORD_USG_IP).get("version")).isEqualTo(before);
        assertThat(events.stream(ImagingOrderStatusChanged.class)).isEmpty();
    }

    @Test
    void cancellingSetsCancelledWithoutActor() throws Exception {
        putStatus(ORD_RTG, sr("revoked", "cancelled")).andExpect(status().isOk());

        assertThat(row(ORD_RTG).get("status")).isEqualTo("cancelled");
        assertThat(events.stream(ImagingOrderStatusChanged.class)).singleElement().satisfies(e -> {
            assertThat(e.actorId()).isNull();
            assertThat(e.note()).isEqualTo("Anulowano w e-imaging");
        });
    }

    @Test
    void cancellingOrderWithSlotReleasesTheSlot() throws Exception {
        String slot = freeSlot("RTG");
        String id = createOrderWithSlot(slot);
        assertThat(slotAvailable(slot)).isFalse();

        putStatus(id, sr("revoked", "cancelled")).andExpect(status().isOk());

        assertThat(row(id).get("status")).isEqualTo("cancelled");
        assertThat(slotAvailable(slot)).isTrue();
    }

    @Test
    void statusWithoutExtensionUsesR4Status() throws Exception {
        putStatus(ORD_USG_IP, "{\"resourceType\":\"ServiceRequest\",\"status\":\"completed\",\"intent\":\"order\","
                + "\"subject\":{\"reference\":\"Patient/x\"}}").andExpect(status().isOk());
        assertThat(row(ORD_USG_IP).get("status")).isEqualTo("completed");
    }

    @Test
    void statusRulesAreEnforced() throws Exception {
        // stan koncowy, niedozwolone przejscie (in_progress -> scheduled) -> 409
        putStatus(ORD_CT, sr("revoked", "cancelled")).andExpect(status().isConflict());
        putStatus(ORD_USG_IP, sr("active", "scheduled")).andExpect(status().isConflict());
        // 'ordered' to nie zmiana, specimen_collected nie dotyczy obrazowania -> 422
        putStatus(ORD_USG_IP, sr("active", "ordered")).andExpect(status().isUnprocessableEntity());
        putStatus(ORD_RTG, sr("active", "specimen_collected")).andExpect(status().isUnprocessableEntity());
        // identyfikator zlecenia z ciala niezgodny -> 422
        putStatus(ORD_RTG, "{\"resourceType\":\"ServiceRequest\",\"status\":\"active\",\"intent\":\"order\","
                + "\"identifier\":[{\"system\":\"urn:his:imaging-order-id\",\"value\":\"inne\"}]}")
                .andExpect(status().isUnprocessableEntity());
        // nieznany status / status R4 spoza mapowania / zly JSON -> 400, nieznane zlecenie -> 404
        putStatus(ORD_RTG, sr("active", "dziwny")).andExpect(status().isBadRequest());
        putStatus(ORD_RTG, "{\"resourceType\":\"ServiceRequest\",\"status\":\"on-hold\",\"intent\":\"order\"}")
                .andExpect(status().isBadRequest());
        putStatus(ORD_RTG, "nie json").andExpect(status().isBadRequest());
        putStatus("00000000-0000-0000-0000-000000000000", sr("active", "ordered")).andExpect(status().isNotFound());
        assertThat(row(ORD_RTG).get("status")).isEqualTo("ordered");
        assertThat(events.stream(ImagingOrderStatusChanged.class)).isEmpty();
    }

    // --- DiagnosticReport ---

    @Test
    void preliminaryReportIsRecordedWithoutCompletingTheOrder() throws Exception {
        String json = postReport(usgReport("preliminary", "2026-10-01T09:00:00Z")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        DiagnosticReport created = fhir.newJsonParser().parseResource(DiagnosticReport.class, json);

        assertThat(created.getStatus()).isEqualTo(DiagnosticReport.DiagnosticReportStatus.PRELIMINARY);
        assertThat(row(ORD_USG_IP).get("status")).isEqualTo("in_progress");
        assertThat(events.stream(ImagingOrderStatusChanged.class)).isEmpty();
    }

    @Test
    void finalReportIsRecordedWithSystemActorAndCompletesOrder() throws Exception {
        String json = postReport(report(ORD_USG_IP, USG_PATIENT, "USG-JB", "final", "2026-10-01T08:00:00Z",
                "2026-10-01T09:00:00Z", "Pecherzyk zolciowy z zlogami.", "Kamica zolciowa.", true))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String resultId = fhir.newJsonParser().parseResource(DiagnosticReport.class, json).getIdElement()
                .getIdPart();

        Map<String, Object> saved = jdbc.queryForMap("select radiologist_name, radiologist_id, findings, conclusion, "
                + "critical, status, order_id::text oid from imaging_result where id = ?::uuid", resultId);
        assertThat(saved).containsEntry("radiologist_name", "Radiolog Test").containsEntry("critical", true)
                .containsEntry("findings", "Pecherzyk zolciowy z zlogami.").containsEntry("status", "final")
                .containsEntry("conclusion", "Kamica zolciowa.").containsEntry("oid", ORD_USG_IP);
        assertThat(saved.get("radiologist_id")).isNull();
        assertThat(events.stream(ImagingResultRecorded.class)).singleElement().satisfies(e -> {
            assertThat(e.critical()).isTrue();
            assertThat(e.actorId()).isNull();
        });
        assertThat(row(ORD_USG_IP).get("status")).isEqualTo("completed");
        assertThat(events.stream(ImagingOrderStatusChanged.class)).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(e.actorId()).isNull();
        });

        DiagnosticReport read = fhir.newJsonParser().parseResource(DiagnosticReport.class,
                mvc.perform(get("/fhir/DiagnosticReport/" + resultId).with(FhirTestAuth.service()))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(read.getConclusion()).isEqualTo("Kamica zolciowa.");
        assertThat(read.getExtensionByUrl("urn:his:fhir:imaging-findings").getValue().primitiveValue())
                .isEqualTo("Pecherzyk zolciowy z zlogami.");
    }

    @Test
    void radiologistDefaultsToServiceNameWhenMissing() throws Exception {
        String body = usgReport("final", "2026-10-01T09:00:00Z")
                .replace("\"performer\":[{\"display\":\"Radiolog Test\"}],", "");
        String json = postReport(body).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        String id = fhir.newJsonParser().parseResource(DiagnosticReport.class, json).getIdElement().getIdPart();
        assertThat(jdbc.queryForObject("select radiologist_name from imaging_result where id = ?::uuid",
                String.class, id)).isEqualTo("e-imaging");
    }

    @Test
    void repeatedReportIsIdempotentEvenAfterTheOrderIsCompleted() throws Exception {
        String body = usgReport("final", "2026-10-01T09:00:00Z");
        String first = postReport(body).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        assertThat(row(ORD_USG_IP).get("status")).isEqualTo("completed");
        String second = postReport(body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(fhir.newJsonParser().parseResource(DiagnosticReport.class, second).getIdElement().getIdPart())
                .isEqualTo(fhir.newJsonParser().parseResource(DiagnosticReport.class, first).getIdElement()
                        .getIdPart());
        assertThat(jdbc.queryForObject("select count(*) from imaging_result where order_id = ?::uuid "
                + "and status = 'final'", Long.class, ORD_USG_IP)).isEqualTo(1L);
    }

    @Test
    void differentReportAfterFinalIsConflict() throws Exception {
        postReport(usgReport("final", "2026-10-01T09:00:00Z")).andExpect(status().isCreated());
        postReport(usgReport("final", "2026-10-01T09:30:00Z")).andExpect(status().isConflict());
    }

    @Test
    void reportForOrderThatIsNotScheduledOrInProgressIsConflict() throws Exception {
        postReport(report(ORD_RTG, RTG_PATIENT, "RTG-KOL", "final", "2026-10-01T08:00:00Z", "2026-10-01T09:00:00Z",
                "Opis.", "Wniosek.", false)).andExpect(status().isConflict());
    }

    @Test
    void invalidReportsAreRejected() throws Exception {
        // kod badania niezgodny ze zleceniem, brak opisu/wnioskow, opis przed wykonaniem, zly pacjent, brak zlecenia -> 422
        postReport(report(ORD_USG_IP, USG_PATIENT, "RTG-KOL", "final", "2026-10-01T08:00:00Z",
                "2026-10-01T09:00:00Z", "Opis.", "Wniosek.", false)).andExpect(status().isUnprocessableEntity());
        postReport(usgReport("final", "2026-10-01T09:00:00Z")
                .replace("\"conclusion\":\"Bez zmian ogniskowych.\",", "")).andExpect(status().isUnprocessableEntity());
        postReport(usgReport("final", "2026-10-01T09:00:00Z").replace("Watroba prawidlowa.", "  "))
                .andExpect(status().isUnprocessableEntity());
        postReport(usgReport("final", "2026-10-01T07:00:00Z")).andExpect(status().isUnprocessableEntity());
        postReport(usgReport("final", "2026-10-01T09:00:00Z").replace(USG_PATIENT, RTG_PATIENT))
                .andExpect(status().isUnprocessableEntity());
        postReport(report("00000000-0000-0000-0000-000000000000", USG_PATIENT, "USG-JB", "final",
                "2026-10-01T08:00:00Z", "2026-10-01T09:00:00Z", "Opis.", "Wniosek.", false))
                .andExpect(status().isUnprocessableEntity());
        // brak basedOn, status spoza mapowania, brak kodu badania, zly JSON -> 400
        postReport(usgReport("final", "2026-10-01T09:00:00Z")
                .replace("\"basedOn\":[{\"reference\":\"ServiceRequest/" + ORD_USG_IP + "\"}],", ""))
                .andExpect(status().isBadRequest());
        postReport(usgReport("corrected", "2026-10-01T09:00:00Z")).andExpect(status().isBadRequest());
        postReport(usgReport("final", "2026-10-01T09:00:00Z").replace("urn:his:imaging-exam", "urn:inny"))
                .andExpect(status().isBadRequest());
        postReport("nie json").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from imaging_result where order_id = ?::uuid", Long.class,
                ORD_USG_IP)).isZero();
    }

    @Test
    void readUnknownReportIsNotFound() throws Exception {
        mvc.perform(get("/fhir/DiagnosticReport/00000000-0000-0000-0000-000000000000").with(FhirTestAuth.service()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/fhir/DiagnosticReport/nie-uuid").with(FhirTestAuth.service()))
                .andExpect(status().isNotFound());
    }

    @Test
    void readReturnsMockImagingResult() throws Exception {
        mvc.perform(get("/fhir/DiagnosticReport/46b4363a-51a4-5eae-9151-05948f291573").with(FhirTestAuth.service()))
                .andExpect(status().isOk()); // wynik obrazowy (mock)
    }

    // --- pomocnicze ---

    private String freeSlot(String modality) {
        return jdbc.queryForObject("select id::text from schedule_slot where modality = ? and available "
                + "order by start_at, room, id limit 1", String.class, modality);
    }

    private boolean slotAvailable(String slotId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select available from schedule_slot where id = ?::uuid",
                Boolean.class, slotId));
    }

    private String createOrderWithSlot(String slot) throws Exception {
        String body = "{\"examCode\":\"RTG-KOL\",\"laterality\":\"left\",\"contrast\":false,"
                + "\"clinicalIndication\":\"Kontrola.\",\"urgency\":\"routine\",\"slotId\":\"" + slot + "\","
                + "\"safety\":" + SAFETY_OK + "}";
        String response = mvc.perform(post("/api/v1/patients/{id}/imaging-orders", RTG_PATIENT)
                        .header(HttpHeaders.AUTHORIZATION, bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(response, "$.id");
    }
}
