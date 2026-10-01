package robert_neat.eimaging.fhir;

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

/** Kontrakt FHIR e-imaging: przyjecie zlecenia z HIS, odczyt, zmiana stanu z HIS i bledy jako OperationOutcome. */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceRequestApiTest {

    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

    @Autowired
    MockMvc mvc;
    @Autowired
    FhirContext fhir;

    /** Zlecenie jak z HIS: RTG kolana prawego, z terminem i slotem. */
    static String order(String hisId, String status, String stage) {
        return """
                {"resourceType":"ServiceRequest","id":"%1$s",
                 "identifier":[{"system":"urn:his:imaging-order-id","value":"%1$s"}],
                 "status":"%2$s","intent":"order","priority":"urgent",
                 "extension":[{"url":"urn:his:fhir:imaging-order-status","valueString":"%3$s"},
                              {"url":"urn:his:fhir:imaging-contrast","valueBoolean":true},
                              {"url":"urn:his:fhir:imaging-slot","valueString":"5d0b1f0e-0000-4000-8000-000000000009"}],
                 "subject":{"reference":"Patient/0f0db024-a3c0-56a5-916d-3acf34a052e5"},
                 "requester":{"reference":"Practitioner/3bc5ba72-1a62-3681-ba58-fa6c83501852"},
                 "authoredOn":"2026-10-01T10:15:30Z","occurrenceDateTime":"2026-10-02T08:00:00Z",
                 "code":{"coding":[{"system":"urn:his:imaging-exam","code":"RTG-KOL","display":"RTG stawu kolanowego"}]},
                 "orderDetail":[{"coding":[{"system":"urn:his:imaging-modality","code":"RTG"},
                                           {"system":"urn:his:imaging-laterality","code":"right"}]}],
                 "bodySite":[{"text":"Staw kolanowy"}],
                 "reasonCode":[{"text":"Bol po urazie."}],
                 "note":[{"text":"Zlamanie?"}]}
                """.formatted(hisId, status, stage);
    }

    static String update(String r4, String wire) {
        return """
                {"resourceType":"ServiceRequest","status":"%s","intent":"order",
                 "extension":[{"url":"urn:his:fhir:imaging-order-status","valueString":"%s"}]}
                """.formatted(r4, wire);
    }

    private ServiceRequest create(String hisId, String stage) throws Exception {
        MvcResult result = mvc.perform(post("/fhir/ServiceRequest").contentType(FHIR_JSON)
                        .content(order(hisId, "active", stage)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/fhir/ServiceRequest/" + hisId)).andReturn();
        return fhir.newJsonParser().parseResource(ServiceRequest.class, result.getResponse().getContentAsString());
    }

    @Test
    void createStoresOrderWithExamModalityLateralityAndTerm() throws Exception {
        String hisId = UUID.randomUUID().toString();
        ServiceRequest created = create(hisId, "scheduled");

        assertThat(created.getIdElement().getIdPart()).isEqualTo(hisId);
        assertThat(created.getExtensionByUrl("urn:his:fhir:imaging-order-status").getValue().primitiveValue())
                .isEqualTo("scheduled");
        assertThat(created.getCode().getCodingFirstRep().getCode()).isEqualTo("RTG-KOL");
        assertThat(created.getOrderDetailFirstRep().getCoding()).extracting(c -> c.getCode())
                .containsExactly("RTG", "right");
        assertThat(created.getBodySiteFirstRep().getText()).isEqualTo("Staw kolanowy");
        assertThat(created.getReasonCodeFirstRep().getText()).isEqualTo("Bol po urazie.");
        assertThat(created.hasOccurrenceDateTimeType()).isTrue();
        assertThat(created.getPriority()).isEqualTo(ServiceRequest.ServiceRequestPriority.URGENT);

        mvc.perform(get("/fhir/ServiceRequest/" + hisId)).andExpect(status().isOk());
    }

    @Test
    void createIsIdempotentPerHisOrder() throws Exception {
        String hisId = UUID.randomUUID().toString();
        create(hisId, "ordered");

        mvc.perform(post("/fhir/ServiceRequest").contentType(FHIR_JSON).content(order(hisId, "active", "ordered")))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsPlainApplicationJson() throws Exception {
        mvc.perform(post("/fhir/ServiceRequest").contentType(MediaType.APPLICATION_JSON)
                        .content(order(UUID.randomUUID().toString(), "active", "ordered")))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidResourcesYieldOperationOutcome() throws Exception {
        MvcResult noId = mvc.perform(post("/fhir/ServiceRequest").contentType(FHIR_JSON)
                        .content("{\"resourceType\":\"ServiceRequest\",\"status\":\"active\",\"intent\":\"order\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        OperationOutcome oo = fhir.newJsonParser().parseResource(OperationOutcome.class,
                noId.getResponse().getContentAsString());
        assertThat(oo.getIssueFirstRep().getDiagnostics()).contains("urn:his:imaging-order-id");

        mvc.perform(post("/fhir/ServiceRequest").contentType(FHIR_JSON).content("not json"))
                .andExpect(status().isBadRequest());
        // status inny niz active, etap koncowy w rozszerzeniu, brak kodu badania
        mvc.perform(post("/fhir/ServiceRequest").contentType(FHIR_JSON)
                        .content(order(UUID.randomUUID().toString(), "revoked", "cancelled")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/fhir/ServiceRequest").contentType(FHIR_JSON)
                        .content(order(UUID.randomUUID().toString(), "active", "completed")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/fhir/ServiceRequest").contentType(FHIR_JSON)
                        .content("""
                                {"resourceType":"ServiceRequest","status":"active","intent":"order",
                                 "identifier":[{"system":"urn:his:imaging-order-id","value":"x"}]}"""))
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
        create(hisId, "scheduled");

        mvc.perform(put("/fhir/ServiceRequest/" + hisId).contentType(FHIR_JSON).content(update("revoked", "cancelled")))
                .andExpect(status().isOk());
        // powtorzenie tego samego stanu jest idempotentne, inny stan ze stanu koncowego to 409
        mvc.perform(put("/fhir/ServiceRequest/" + hisId).contentType(FHIR_JSON).content(update("revoked", "cancelled")))
                .andExpect(status().isOk());
        mvc.perform(put("/fhir/ServiceRequest/" + hisId).contentType(FHIR_JSON)
                        .content(update("active", "in_progress")))
                .andExpect(status().isConflict());
    }

    @Test
    void statusWithoutExtensionUsesFhirStatus() throws Exception {
        String hisId = UUID.randomUUID().toString();
        create(hisId, "ordered");
        mvc.perform(put("/fhir/ServiceRequest/" + hisId).contentType(FHIR_JSON)
                        .content("{\"resourceType\":\"ServiceRequest\",\"status\":\"revoked\",\"intent\":\"order\"}"))
                .andExpect(status().isOk());
        MvcResult read = mvc.perform(get("/fhir/ServiceRequest/" + hisId)).andReturn();
        assertThat(read.getResponse().getContentAsString()).contains("\"revoked\"").contains("\"cancelled\"");
    }
}
