package robert_neat.elaboratory.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ca.uhn.fhir.context.FhirContext;

/** Kontrakt FHIR e-laboratory: przyjecie zlecenia z HIS, odczyt, zmiana stanu z HIS i bledy jako OperationOutcome. */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceRequestApiTest {

    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

    @Autowired
    MockMvc mvc;
    @Autowired
    FhirContext fhir;

    /** Zlecenie jak z HIS: dwa badania, analit z zakresem i bez, material, na czczo. */
    static String order(String hisId, String status) {
        return """
                {"resourceType":"ServiceRequest","id":"%1$s",
                 "identifier":[{"system":"urn:his:lab-order-id","value":"%1$s"}],
                 "status":"%2$s","intent":"order","priority":"urgent",
                 "subject":{"reference":"Patient/0f0db024-a3c0-56a5-916d-3acf34a052e5"},
                 "requester":{"reference":"Practitioner/3bc5ba72-1a62-3681-ba58-fa6c83501852"},
                 "authoredOn":"2026-10-01T10:15:30Z","occurrenceDateTime":"2026-10-02T08:00:00Z",
                 "patientInstruction":"Na czczo",
                 "orderDetail":[
                  {"coding":[{"system":"urn:his:lab-test","code":"MORF","display":"Morfologia"},
                             {"system":"urn:his:specimen-type","code":"blood"}],
                   "extension":[{"url":"urn:his:fhir:lab-analyte","extension":[
                     {"url":"code","valueString":"HGB"},{"url":"name","valueString":"Hemoglobina"},
                     {"url":"unit","valueString":"g/dL"},{"url":"low","valueString":"12"},
                     {"url":"high","valueString":"16"}]}]},
                  {"coding":[{"system":"urn:his:lab-test","code":"CRP","display":"CRP"}]}],
                 "note":[{"text":"Kontrola."}]}
                """.formatted(hisId, status);
    }

    static String update(String r4, String wire) {
        return """
                {"resourceType":"ServiceRequest","status":"%s","intent":"order",
                 "extension":[{"url":"urn:his:fhir:lab-order-status","valueString":"%s"}]}
                """.formatted(r4, wire);
    }

    private ServiceRequest create(String hisId) throws Exception {
        MvcResult result = mvc.perform(post("/fhir/ServiceRequest").contentType(FHIR_JSON)
                        .content(order(hisId, "active")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/fhir/ServiceRequest/" + hisId)).andReturn();
        return fhir.newJsonParser().parseResource(ServiceRequest.class, result.getResponse().getContentAsString());
    }

    @Test
    void createStoresOrderWithItemsAndAnalytes() throws Exception {
        String hisId = UUID.randomUUID().toString();
        ServiceRequest created = create(hisId);

        assertThat(created.getIdElement().getIdPart()).isEqualTo(hisId);
        assertThat(created.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.ACTIVE);
        assertThat(created.getOrderDetail()).extracting(d -> d.getCodingFirstRep().getCode())
                .containsExactly("MORF", "CRP");
        assertThat(created.getOrderDetail().get(0).getExtensionsByUrl("urn:his:fhir:lab-analyte")).hasSize(1);
        assertThat(created.getPatientInstruction()).isEqualTo("Na czczo");

        mvc.perform(get("/fhir/ServiceRequest/" + hisId)).andExpect(status().isOk());
    }

    @Test
    void createIsIdempotentPerHisOrder() throws Exception {
        String hisId = UUID.randomUUID().toString();
        create(hisId);

        mvc.perform(post("/fhir/ServiceRequest").contentType(FHIR_JSON).content(order(hisId, "active")))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsPlainApplicationJson() throws Exception {
        mvc.perform(post("/fhir/ServiceRequest").contentType(MediaType.APPLICATION_JSON)
                        .content(order(UUID.randomUUID().toString(), "active")))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidResourcesYieldOperationOutcome() throws Exception {
        MvcResult noId = mvc.perform(post("/fhir/ServiceRequest").contentType(FHIR_JSON)
                        .content("{\"resourceType\":\"ServiceRequest\",\"status\":\"active\",\"intent\":\"order\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        OperationOutcome oo = fhir.newJsonParser().parseResource(OperationOutcome.class,
                noId.getResponse().getContentAsString());
        assertThat(oo.getIssueFirstRep().getDiagnostics()).contains("urn:his:lab-order-id");

        mvc.perform(post("/fhir/ServiceRequest").contentType(FHIR_JSON).content("not json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/fhir/ServiceRequest").contentType(FHIR_JSON)
                        .content(order(UUID.randomUUID().toString(), "revoked")))
                .andExpect(status().isBadRequest());
        // brak badan
        mvc.perform(post("/fhir/ServiceRequest").contentType(FHIR_JSON)
                        .content("""
                                {"resourceType":"ServiceRequest","status":"active","intent":"order",
                                 "identifier":[{"system":"urn:his:lab-order-id","value":"x"}]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrderIs404() throws Exception {
        mvc.perform(get("/fhir/ServiceRequest/UNKNOWN")).andExpect(status().isNotFound());
        mvc.perform(put("/fhir/ServiceRequest/UNKNOWN").contentType(FHIR_JSON).content(update("revoked", "cancelled")))
                .andExpect(status().isNotFound());
    }

    @Test
    void hisCanCancelAndTerminalStateRejectsFurtherChanges() throws Exception {
        String hisId = UUID.randomUUID().toString();
        create(hisId);

        mvc.perform(put("/fhir/ServiceRequest/" + hisId).contentType(FHIR_JSON).content(update("revoked", "cancelled")))
                .andExpect(status().isOk());
        // powtorzenie tego samego stanu jest idempotentne, inny stan ze stanu koncowego to 409
        mvc.perform(put("/fhir/ServiceRequest/" + hisId).contentType(FHIR_JSON).content(update("revoked", "cancelled")))
                .andExpect(status().isOk());
        mvc.perform(put("/fhir/ServiceRequest/" + hisId).contentType(FHIR_JSON)
                        .content(update("active", "specimen_collected")))
                .andExpect(status().isConflict());
    }

    @Test
    void statusWithoutExtensionUsesFhirStatus() throws Exception {
        String hisId = UUID.randomUUID().toString();
        create(hisId);
        mvc.perform(put("/fhir/ServiceRequest/" + hisId).contentType(FHIR_JSON)
                        .content("{\"resourceType\":\"ServiceRequest\",\"status\":\"revoked\",\"intent\":\"order\"}"))
                .andExpect(status().isOk());
        MvcResult read = mvc.perform(get("/fhir/ServiceRequest/" + hisId)).andReturn();
        assertThat(read.getResponse().getContentAsString()).contains("\"revoked\"").contains("\"cancelled\"");
    }
}
