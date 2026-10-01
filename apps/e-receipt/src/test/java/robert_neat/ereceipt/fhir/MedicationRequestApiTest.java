package robert_neat.ereceipt.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ca.uhn.fhir.context.FhirContext;

/** Kontrakt FHIR e-receipt: przyjecie recepty z HIS, odczyt, zmiana stanu z HIS i bledy jako OperationOutcome. */
@SpringBootTest
@AutoConfigureMockMvc
class MedicationRequestApiTest {

    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");
    private static final String VALID_FROM = "2026-10-01";
    private static final String VALID_UNTIL = "2026-10-31";

    @Autowired
    MockMvc mvc;
    @Autowired
    FhirContext fhir;

    static String prescription(String hisId, String status) {
        return """
                {"resourceType":"MedicationRequest",
                 "identifier":[{"system":"urn:his:prescription-id","value":"%s"},
                               {"system":"urn:his:access-code","value":"1234"}],
                 "status":"%s","intent":"order",
                 "subject":{"reference":"Patient/p-1"},
                 "requester":{"reference":"Practitioner/s-1"},
                 "authoredOn":"2026-10-01T10:15:30Z",
                 "medicationCodeableConcept":{"text":"Polpril 5 mg tabl."},
                 "dosageInstruction":[{"text":"1 tabl. QD p.o., 30 dni"}],
                 "dispenseRequest":{"validityPeriod":{"start":"%s","end":"%s"}}}
                """.formatted(hisId, status, VALID_FROM, VALID_UNTIL);
    }

    private MedicationRequest create(String hisId) throws Exception {
        MvcResult result = mvc.perform(post("/fhir/MedicationRequest").contentType(FHIR_JSON)
                        .content(prescription(hisId, "active")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/fhir/MedicationRequest/")))
                .andReturn();
        return fhir.newJsonParser().parseResource(MedicationRequest.class,
                result.getResponse().getContentAsString());
    }

    private static String update(String status, String wire) {
        return """
                {"resourceType":"MedicationRequest","status":"%s","intent":"order",
                 "extension":[{"url":"urn:his:fhir:prescription-status","valueString":"%s"}],
                 "medicationCodeableConcept":{"text":"x"}}
                """.formatted(status, wire);
    }

    @Test
    void createReturnsErxKeyAndStoresPrescription() throws Exception {
        String hisId = UUID.randomUUID().toString();
        MedicationRequest created = create(hisId);

        String key = created.getIdElement().getIdPart();
        assertThat(key).matches("[A-Z0-9]{44}");
        assertThat(created.getIdentifier()).anySatisfy(i -> {
            assertThat(i.getSystem()).isEqualTo("urn:his:erx-key");
            assertThat(i.getValue()).isEqualTo(key);
        });
        assertThat(created.getStatus()).isEqualTo(MedicationRequest.MedicationRequestStatus.ACTIVE);

        mvc.perform(get("/fhir/MedicationRequest/" + key)).andExpect(status().isOk());
    }

    @Test
    void createIsIdempotentPerHisPrescription() throws Exception {
        String hisId = UUID.randomUUID().toString();
        String key = create(hisId).getIdElement().getIdPart();

        MvcResult again = mvc.perform(post("/fhir/MedicationRequest").contentType(FHIR_JSON)
                        .content(prescription(hisId, "active")))
                .andExpect(status().isOk()).andReturn();
        MedicationRequest second = fhir.newJsonParser().parseResource(MedicationRequest.class,
                again.getResponse().getContentAsString());
        assertThat(second.getIdElement().getIdPart()).isEqualTo(key);
    }

    @Test
    void acceptsPlainApplicationJson() throws Exception {
        mvc.perform(post("/fhir/MedicationRequest").contentType(MediaType.APPLICATION_JSON)
                        .content(prescription(UUID.randomUUID().toString(), "active")))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidResourcesYieldOperationOutcome() throws Exception {
        MvcResult noId = mvc.perform(post("/fhir/MedicationRequest").contentType(FHIR_JSON)
                        .content("""
                                {"resourceType":"MedicationRequest","status":"active","intent":"order",
                                 "medicationCodeableConcept":{"text":"x"}}"""))
                .andExpect(status().isBadRequest()).andReturn();
        OperationOutcome oo = fhir.newJsonParser().parseResource(OperationOutcome.class,
                noId.getResponse().getContentAsString());
        assertThat(oo.getIssueFirstRep().getDiagnostics()).contains("urn:his:prescription-id");

        mvc.perform(post("/fhir/MedicationRequest").contentType(FHIR_JSON).content("not json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/fhir/MedicationRequest").contentType(FHIR_JSON)
                        .content(prescription(UUID.randomUUID().toString(), "cancelled")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownKeyIs404() throws Exception {
        mvc.perform(get("/fhir/MedicationRequest/UNKNOWN")).andExpect(status().isNotFound());
        mvc.perform(put("/fhir/MedicationRequest/UNKNOWN").contentType(FHIR_JSON)
                        .content(update("cancelled", "cancelled")))
                .andExpect(status().isNotFound());
    }

    @Test
    void hisCanCancelAndTerminalStateRejectsFurtherChanges() throws Exception {
        String key = create(UUID.randomUUID().toString()).getIdElement().getIdPart();

        mvc.perform(put("/fhir/MedicationRequest/" + key).contentType(FHIR_JSON)
                        .content(update("cancelled", "cancelled")))
                .andExpect(status().isOk());
        // powtorzenie tego samego stanu jest idempotentne, inny stan z koncowego to 409
        mvc.perform(put("/fhir/MedicationRequest/" + key).contentType(FHIR_JSON)
                        .content(update("cancelled", "cancelled")))
                .andExpect(status().isOk());
        mvc.perform(put("/fhir/MedicationRequest/" + key).contentType(FHIR_JSON)
                        .content(update("completed", "dispensed")))
                .andExpect(status().isConflict());
    }

    @Test
    void statusWithoutExtensionUsesFhirStatus() throws Exception {
        String key = create(UUID.randomUUID().toString()).getIdElement().getIdPart();
        mvc.perform(put("/fhir/MedicationRequest/" + key).contentType(FHIR_JSON)
                        .content("""
                                {"resourceType":"MedicationRequest","status":"completed","intent":"order",
                                 "medicationCodeableConcept":{"text":"x"}}"""))
                .andExpect(status().isOk());
        MvcResult read = mvc.perform(get("/fhir/MedicationRequest/" + key)).andReturn();
        assertThat(read.getResponse().getContentAsString()).contains("\"completed\"").contains("\"dispensed\"");
    }
}
