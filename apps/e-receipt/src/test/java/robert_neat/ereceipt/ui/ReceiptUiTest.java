package robert_neat.ereceipt.ui;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.MedicationRequest;

/** UI Thymeleaf dostepne bez uwierzytelniania: lista i zmiana stanu (integracja z HIS wylaczona w testach). */
@SpringBootTest
@AutoConfigureMockMvc
class ReceiptUiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    FhirContext fhir;

    private String createViaFhir() throws Exception {
        String json = """
                {"resourceType":"MedicationRequest","status":"active","intent":"order",
                 "identifier":[{"system":"urn:his:prescription-id","value":"%s"}],
                 "medicationCodeableConcept":{"text":"Atoris 20 mg"},
                 "dispenseRequest":{"validityPeriod":{"start":"2026-10-01","end":"2026-10-31"}}}
                """.formatted(UUID.randomUUID());
        String body = mvc.perform(post("/fhir/MedicationRequest")
                        .contentType(MediaType.valueOf("application/fhir+json")).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return fhir.newJsonParser().parseResource(MedicationRequest.class, body).getIdElement().getIdPart();
    }

    @Test
    void rootRedirectsToList() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/ui/prescriptions"));
    }

    @Test
    void listShowsReceiptWithoutAuthentication() throws Exception {
        String key = createViaFhir();
        mvc.perform(get("/ui/prescriptions")).andExpect(status().isOk())
                .andExpect(content().string(containsString(key)))
                .andExpect(content().string(containsString("Atoris 20 mg")));
    }

    @Test
    void changingStateUpdatesReceiptAndHandlesConflicts() throws Exception {
        String key = createViaFhir();
        mvc.perform(post("/ui/prescriptions/" + key + "/status").param("status", "dispensed"))
                .andExpect(redirectedUrl("/ui/prescriptions"))
                .andExpect(flash().attributeExists("message"));
        mvc.perform(get("/fhir/MedicationRequest/" + key))
                .andExpect(content().string(containsString("\"completed\"")));
        // stan koncowy: kolejna zmiana to blad pokazany w UI
        mvc.perform(post("/ui/prescriptions/" + key + "/status").param("status", "cancelled"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/ui/prescriptions/" + key + "/status").param("status", "bogus"))
                .andExpect(flash().attributeExists("error"));
    }
}
