package robert_neat.eimaging.ui;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

/** UI Thymeleaf dostepne bez uwierzytelniania: lista, zmiana stanu i wynik (integracja z HIS wylaczona w testach). */
@SpringBootTest
@AutoConfigureMockMvc
class ImagingUiTest {

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
    void rootRedirectsToList() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/ui/orders"));
    }

    @Test
    void listShowsOrderWithoutAuthentication() throws Exception {
        String id = createViaFhir();
        mvc.perform(get("/ui/orders")).andExpect(status().isOk())
                .andExpect(content().string(containsString(id)))
                .andExpect(content().string(containsString("USG UI")))
                .andExpect(content().string(containsString("Wskazanie UI")));
    }

    @Test
    void changingStateUpdatesOrderAndHandlesConflicts() throws Exception {
        String id = createViaFhir();
        mvc.perform(post("/ui/orders/" + id + "/status").param("status", "in_progress"))
                .andExpect(redirectedUrl("/ui/orders")).andExpect(flash().attributeExists("message"));
        mvc.perform(get("/fhir/ServiceRequest/" + id))
                .andExpect(content().string(containsString("in_progress")));
        // niedozwolone przejscie i nieznany stan to bledy pokazane w UI
        mvc.perform(post("/ui/orders/" + id + "/status").param("status", "ordered"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/ui/orders/" + id + "/status").param("status", "bogus"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void resultFormRecordsFinalResultAndCompletesOrder() throws Exception {
        String id = createViaFhir();
        mvc.perform(get("/ui/orders")).andExpect(content().string(containsString("Wprowadz wynik")));
        mvc.perform(post("/ui/orders/" + id + "/result").param("status", "final").param("findings", "Opis UI.")
                        .param("conclusion", "Wniosek UI.").param("critical", "true").param("radiologist", "Radiolog UI"))
                .andExpect(redirectedUrl("/ui/orders")).andExpect(flash().attributeExists("message"));

        mvc.perform(get("/ui/orders")).andExpect(content().string(containsString("Opis: Opis UI.")))
                .andExpect(content().string(containsString("KRYTYCZNY")));
        mvc.perform(get("/fhir/ServiceRequest/" + id)).andExpect(content().string(containsString("\"completed\"")));
        // zlecenie zakonczone nie przyjmuje kolejnego wyniku
        mvc.perform(post("/ui/orders/" + id + "/result").param("status", "final").param("findings", "x")
                        .param("conclusion", "y"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void resultFormIsHiddenBeforeScheduling() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(post("/fhir/ServiceRequest").contentType(MediaType.valueOf("application/fhir+json"))
                .content("""
                        {"resourceType":"ServiceRequest","status":"active","intent":"order",
                         "identifier":[{"system":"urn:his:imaging-order-id","value":"%s"}],
                         "code":{"coding":[{"system":"urn:his:imaging-exam","code":"RTG-X","display":"RTG bez terminu"}]}}
                        """.formatted(id))).andExpect(status().isCreated());
        mvc.perform(post("/ui/orders/" + id + "/result").param("status", "final").param("findings", "x")
                        .param("conclusion", "y"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(get("/ui/orders")).andExpect(content().string(containsString("RTG bez terminu")))
                .andExpect(content().string(not(containsString("Opis: x"))));
    }

    @Test
    void resultFormRejectsEmptyAndInvalidInput() throws Exception {
        String id = createViaFhir();
        mvc.perform(post("/ui/orders/" + id + "/result").param("status", "final"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/ui/orders/" + id + "/result").param("status", "final").param("findings", "Opis"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/ui/orders/" + id + "/result").param("status", "zly").param("findings", "a")
                        .param("conclusion", "b"))
                .andExpect(flash().attributeExists("error"));
    }
}
