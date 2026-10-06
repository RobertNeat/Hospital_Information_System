package robert_neat.eimaging.ui;

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

    private String createViaFhir() throws Exception {
        String id = UUID.randomUUID().toString();
        String json = """
                {"resourceType":"ServiceRequest","status":"active","intent":"order",
                 "identifier":[{"system":"urn:his:imaging-order-id","value":"%s"}],
                 "extension":[{"url":"urn:his:fhir:imaging-order-status","valueString":"scheduled"}],
                 "subject":{"reference":"Patient/0f0db024-a3c0-56a5-916d-3acf34a052e5"},
                 "code":{"coding":[{"system":"urn:his:imaging-exam","code":"USG-JB","display":"USG UI"}]},
                 "orderDetail":[{"coding":[{"system":"urn:his:imaging-modality","code":"USG"}]}],
                 "bodySite":[{"text":"Jama brzuszna"}],"reasonCode":[{"text":"Wskazanie UI"}]}
                """.formatted(id);
        mvc.perform(post("/fhir/ServiceRequest").contentType(MediaType.valueOf("application/fhir+json"))
                .content(json)).andExpect(status().isCreated());
        return id;
    }

    @Test
    void realCsrfTokenFromRenderedPageIsAccepted() throws Exception {
        String id = createViaFhir();
        MockHttpServletResponse listResponse = mvc.perform(get("/ui/orders"))
                .andExpect(status().isOk()).andReturn().getResponse();

        String token = extractCsrfToken(listResponse.getContentAsString());
        var xsrfCookie = listResponse.getCookie("XSRF-TOKEN");
        assertNotNull(xsrfCookie, "CookieCsrfTokenRepository powinien ustawic cookie XSRF-TOKEN");

        mvc.perform(post("/ui/orders/" + id + "/status")
                        .param("status", "in_progress")
                        .param("_csrf", token)
                        .cookie(xsrfCookie))
                .andExpect(redirectedUrl("/ui/orders"))
                .andExpect(flash().attributeExists("message"));
    }

    private static String extractCsrfToken(String html) {
        Matcher m = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(html);
        if (!m.find()) throw new AssertionError("brak pola _csrf w wyrenderowanej stronie");
        return m.group(1);
    }
}
