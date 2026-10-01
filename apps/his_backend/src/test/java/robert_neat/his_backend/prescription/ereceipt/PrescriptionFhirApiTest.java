package robert_neat.his_backend.prescription.ereceipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ca.uhn.fhir.context.FhirContext;
import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.prescription.PrescriptionRepository;
import robert_neat.his_backend.prescription.events.PrescriptionCancelled;

/** Endpoint FHIR `/fhir/MedicationRequest` dla e-receipt na danych mock (klucz uslugowy z konfiguracji testowej). */
@RecordApplicationEvents
class PrescriptionFhirApiTest extends ApiIntegrationTest {

    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");
    private static final String KEY = "test-only-service-key";

    private static final String RX_ISSUED = "75f6263c-d428-593d-95e7-94a0e15cfc2c"; // issued
    private static final String RX_ISSUED_2 = "2dfc74df-8654-5e27-af87-3c315a71d733"; // issued
    private static final String RX_ISSUED_3 = "d1649347-8cd7-5a2c-9257-77c333ee9fc1"; // issued
    private static final String RX_DISPENSED = "6fda3a2b-0088-5373-abfb-ba37847eb915"; // dispensed
    private static final String RX_EXPIRED = "83e7df14-c19a-5f9b-b43f-70ed69178558"; // expired (zapisany)

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FhirContext fhir;
    @Autowired
    private ApplicationEvents events;
    @Autowired
    private PrescriptionRepository prescriptions;

    private static String body(String status, String wire) {
        return """
                {"resourceType":"MedicationRequest","status":"%s","intent":"order",
                 "extension":[{"url":"urn:his:fhir:prescription-status","valueString":"%s"}],
                 "medicationCodeableConcept":{"text":"x"}}
                """.formatted(status, wire);
    }

    private ResultActions putStatus(String id, String body) throws Exception {
        return mvc.perform(put("/fhir/MedicationRequest/{id}", id).header("X-Service-Key", KEY)
                .contentType(FHIR_JSON).content(body));
    }

    private Map<String, Object> row(String id) {
        return jdbc.queryForMap("select status, version, cancel_reason, cancelled_at, updated_by_id "
                + "from prescription where id = ?::uuid", id);
    }

    // --- autoryzacja kluczem uslugowym ---

    @Test
    void requestsWithoutOrWithWrongKeyAreUnauthorizedOperationOutcome() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get("/fhir/MedicationRequest/" + RX_ISSUED),
                get("/fhir/MedicationRequest/" + RX_ISSUED).header("X-Service-Key", "zly-klucz"),
                get("/fhir/MedicationRequest/" + RX_ISSUED).header("X-Service-Key", ""),
                put("/fhir/MedicationRequest/" + RX_ISSUED).contentType(FHIR_JSON).content(body("cancelled", "cancelled"))}) {
            String json = mvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(FHIR_JSON))
                    .andReturn().getResponse().getContentAsString();
            assertThat(fhir.newJsonParser().parseResource(OperationOutcome.class, json).getIssueFirstRep()
                    .getCode().toCode()).isEqualTo("security");
        }
        assertThat(row(RX_ISSUED).get("status")).isEqualTo("issued");
    }

    @Test
    void jwtDoesNotAuthorizeFhirEndpoint() throws Exception {
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(login, "$.accessToken");
        mvc.perform(get("/fhir/MedicationRequest/" + RX_ISSUED).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // --- odczyt ---

    @Test
    void readReturnsMedicationRequestWithIdentifiersAndEffectiveStatus() throws Exception {
        String json = mvc.perform(get("/fhir/MedicationRequest/" + RX_ISSUED).header("X-Service-Key", KEY))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(FHIR_JSON))
                .andReturn().getResponse().getContentAsString();
        MedicationRequest mr = fhir.newJsonParser().parseResource(MedicationRequest.class, json);

        assertThat(mr.getStatus()).isEqualTo(MedicationRequest.MedicationRequestStatus.ACTIVE);
        assertThat(mr.getIdentifier()).extracting(i -> i.getSystem() + "=" + i.getValue()).contains(
                "urn:his:prescription-id=" + RX_ISSUED,
                "urn:his:erx-key=254AEYJLX5EJ0R5MFPACPM5GZUIJMPG97MK1VAQBRLVS", "urn:his:access-code=4821");
        assertThat(mr.getMedicationCodeableConcept().getText()).isNotBlank();
        assertThat(mr.getDosageInstruction()).isNotEmpty();
        assertThat(mr.getDispenseRequest().getValidityPeriod().hasStart()).isTrue();
    }

    @Test
    void readUnknownOrMalformedIdIsNotFound() throws Exception {
        mvc.perform(get("/fhir/MedicationRequest/nie-uuid").header("X-Service-Key", KEY))
                .andExpect(status().isNotFound());
        mvc.perform(get("/fhir/MedicationRequest/00000000-0000-0000-0000-000000000000").header("X-Service-Key", KEY))
                .andExpect(status().isNotFound());
    }

    // --- zmiana stanu ---

    @Test
    void dispensingUpdatesStatusAndBumpsVersion() throws Exception {
        long before = (long) row(RX_ISSUED).get("version");
        putStatus(RX_ISSUED, body("completed", "dispensed")).andExpect(status().isOk());

        Map<String, Object> after = row(RX_ISSUED);
        assertThat(after.get("status")).isEqualTo("dispensed");
        assertThat((long) after.get("version")).isGreaterThan(before);
        assertThat(after.get("updated_by_id")).isNull();
        assertThat(events.stream(PrescriptionCancelled.class)).isEmpty();
    }

    @Test
    void partiallyDispensedNeedsExtension() throws Exception {
        putStatus(RX_ISSUED, body("active", "partially_dispensed")).andExpect(status().isOk());
        assertThat(row(RX_ISSUED).get("status")).isEqualTo("partially_dispensed");
        // z "zywego" stanu czesciowej realizacji mozna dalej zrealizowac
        putStatus(RX_ISSUED, body("completed", "dispensed")).andExpect(status().isOk());
        assertThat(row(RX_ISSUED).get("status")).isEqualTo("dispensed");
    }

    @Test
    void cancellingFromEReceiptStoresReasonAndPublishesEventWithoutActor() throws Exception {
        putStatus(RX_ISSUED_2, body("cancelled", "cancelled")).andExpect(status().isOk());

        Map<String, Object> after = row(RX_ISSUED_2);
        assertThat(after.get("status")).isEqualTo("cancelled");
        assertThat(after.get("cancel_reason")).isEqualTo("Anulowano w e-receipt");
        assertThat(after.get("cancelled_at")).isNotNull();
        assertThat(events.stream(PrescriptionCancelled.class)).singleElement().satisfies(e -> {
            assertThat(e.actorId()).isNull();
            assertThat(e.prescriptionId().toString()).isEqualTo(RX_ISSUED_2);
        });
    }

    @Test
    void statusWithoutExtensionUsesR4Status() throws Exception {
        putStatus(RX_ISSUED_3, """
                {"resourceType":"MedicationRequest","status":"stopped","intent":"order",
                 "medicationCodeableConcept":{"text":"x"}}""").andExpect(status().isOk());
        assertThat(row(RX_ISSUED_3).get("status")).isEqualTo("expired");
    }

    @Test
    void repeatingCurrentStatusIsIdempotent() throws Exception {
        putStatus(RX_ISSUED, body("cancelled", "cancelled")).andExpect(status().isOk());
        long version = (long) row(RX_ISSUED).get("version");
        putStatus(RX_ISSUED, body("cancelled", "cancelled")).andExpect(status().isOk());
        assertThat((long) row(RX_ISSUED).get("version")).isEqualTo(version);
        assertThat(events.stream(PrescriptionCancelled.class)).hasSize(1);
        // zrealizowana recepta potwierdzona jeszcze raz tez daje 200
        putStatus(RX_DISPENSED, body("completed", "dispensed")).andExpect(status().isOk());
    }

    @Test
    void terminalPrescriptionsRejectOtherStates() throws Exception {
        putStatus(RX_DISPENSED, body("cancelled", "cancelled")).andExpect(status().isConflict());
        putStatus(RX_EXPIRED, body("completed", "dispensed")).andExpect(status().isConflict());
        assertThat(row(RX_DISPENSED).get("status")).isEqualTo("dispensed");
        assertThat(events.stream(PrescriptionCancelled.class)).isEmpty();
    }

    @Test
    void prescriptionPastValidityOnlyAcceptsExpired() throws Exception {
        // zapisana jako issued, ale po terminie: w HIS jest efektywnie wygasla
        jdbc.update("update prescription set valid_from = current_date - 20, valid_until = current_date - 2 "
                + "where id = ?::uuid", RX_ISSUED);
        putStatus(RX_ISSUED, body("completed", "dispensed")).andExpect(status().isConflict());
        putStatus(RX_ISSUED, body("cancelled", "cancelled")).andExpect(status().isConflict());
        assertThat(row(RX_ISSUED).get("status")).isEqualTo("issued");

        putStatus(RX_ISSUED, body("stopped", "expired")).andExpect(status().isOk());
        assertThat(row(RX_ISSUED).get("status")).isEqualTo("expired");
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        putStatus(RX_ISSUED, body("active", "partially_dispensed")).andExpect(status().isOk());
        putStatus(RX_ISSUED, body("active", "issued")).andExpect(status().isUnprocessableContent());
        putStatus(RX_ISSUED, body("completed", "nieznany")).andExpect(status().isBadRequest());
        putStatus(RX_ISSUED, body("draft", "")).andExpect(status().isBadRequest());
        putStatus(RX_ISSUED, "to nie jest JSON").andExpect(status().isBadRequest());
        putStatus("00000000-0000-0000-0000-000000000000", body("cancelled", "cancelled"))
                .andExpect(status().isNotFound());
        assertThat(row(RX_ISSUED).get("status")).isEqualTo("partially_dispensed");
    }

    @Test
    void mismatchedErxKeyIsUnprocessable() throws Exception {
        putStatus(RX_ISSUED, """
                {"resourceType":"MedicationRequest","status":"completed","intent":"order",
                 "identifier":[{"system":"urn:his:erx-key","value":"INNYKLUCZ"}],
                 "medicationCodeableConcept":{"text":"x"}}""").andExpect(status().isUnprocessableContent());
        assertThat(row(RX_ISSUED).get("status")).isEqualTo("issued");
    }

    // --- zapis klucza e-recepty ---

    @Test
    void updatingErxKeyDoesNotBumpVersion() {
        String newKey = "0123456789".repeat(4) + "ABCD";
        long before = (long) row(RX_ISSUED).get("version");

        assertThat(prescriptions.updateERxKey(java.util.UUID.fromString(RX_ISSUED), newKey)).isEqualTo(1);

        assertThat(jdbc.queryForObject("select erx_key from prescription where id = ?::uuid", String.class,
                RX_ISSUED)).isEqualTo(newKey);
        assertThat((long) row(RX_ISSUED).get("version")).isEqualTo(before);
    }
}
