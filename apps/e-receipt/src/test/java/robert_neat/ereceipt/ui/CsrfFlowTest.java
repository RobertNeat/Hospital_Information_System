package robert_neat.ereceipt.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.web.servlet.MockMvc;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.MedicationRequest;

/**
 * Prawdziwy przeplyw CSRF, BEZ {@code with(csrf())} (ten post-processor podmienia reflexywnie repozytorium
 * tokenu we wspoldzielonym CsrfFilter na sesyjne, trwale dla calego kontekstu testowego - stad osobna klasa z
 * {@code DirtiesContext} przed klasa, aby dostac swiezy, niepodmieniony filtr).
 * GET renderuje pole {@code _csrf} (CsrfRequestDataValueProcessor) i ustawia cookie XSRF-TOKEN
 * (CookieCsrfTokenRepository); POST z tym tokenem i cookie przechodzi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
class CsrfFlowTest {

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
    void realCsrfTokenFromRenderedPageIsAccepted() throws Exception {
        String key = createViaFhir();
        MockHttpServletResponse listResponse = mvc.perform(get("/ui/prescriptions"))
                .andExpect(status().isOk()).andReturn().getResponse();

        String token = extractCsrfToken(listResponse.getContentAsString());
        var xsrfCookie = listResponse.getCookie("XSRF-TOKEN");
        assertNotNull(xsrfCookie, "CookieCsrfTokenRepository powinien ustawic cookie XSRF-TOKEN");

        mvc.perform(post("/ui/prescriptions/" + key + "/status")
                        .param("status", "dispensed")
                        .param("_csrf", token)
                        .cookie(xsrfCookie))
                .andExpect(redirectedUrl("/ui/prescriptions"))
                .andExpect(flash().attributeExists("message"));
    }

    private static String extractCsrfToken(String html) {
        Matcher m = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(html);
        if (!m.find()) throw new AssertionError("brak pola _csrf w wyrenderowanej stronie");
        return m.group(1);
    }
}
