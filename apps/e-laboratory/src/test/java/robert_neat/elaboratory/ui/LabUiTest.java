package robert_neat.elaboratory.ui;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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
class LabUiTest {

    @Autowired
    MockMvc mvc;

    private String createViaFhir() throws Exception {
        String id = UUID.randomUUID().toString();
        String json = """
                {"resourceType":"ServiceRequest","status":"active","intent":"order",
                 "identifier":[{"system":"urn:his:lab-order-id","value":"%s"}],
                 "subject":{"reference":"Patient/0f0db024-a3c0-56a5-916d-3acf34a052e5"},
                 "orderDetail":[{"coding":[{"system":"urn:his:lab-test","code":"MORF","display":"Morfologia UI"}],
                   "extension":[{"url":"urn:his:fhir:lab-analyte","extension":[
                     {"url":"code","valueString":"HGB"},{"url":"name","valueString":"Hemoglobina"},
                     {"url":"unit","valueString":"g/dL"}]}]}]}
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
                .andExpect(content().string(containsString("Morfologia UI")));
    }

    @Test
    void changingStateUpdatesOrderAndHandlesConflicts() throws Exception {
        String id = createViaFhir();
        mvc.perform(post("/ui/orders/" + id + "/status").param("status", "specimen_collected").with(csrf()))
                .andExpect(redirectedUrl("/ui/orders")).andExpect(flash().attributeExists("message"));
        mvc.perform(get("/fhir/ServiceRequest/" + id))
                .andExpect(content().string(containsString("specimen_collected")));
        // niedozwolone przejscie i nieznany stan to bledy pokazane w UI
        mvc.perform(post("/ui/orders/" + id + "/status").param("status", "ordered").with(csrf()))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/ui/orders/" + id + "/status").param("status", "bogus").with(csrf()))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void changingStateWithoutCsrfTokenIsRejected() throws Exception {
        String id = createViaFhir();
        mvc.perform(post("/ui/orders/" + id + "/status").param("status", "specimen_collected"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resultFormRecordsResultAndCompletesOrder() throws Exception {
        String id = createViaFhir();
        // przed pobraniem materialu wynik jest odrzucany
        mvc.perform(post("/ui/orders/" + id + "/results/MORF").param("status", "final").param("v_HGB", "14,2")
                        .with(csrf()))
                .andExpect(flash().attributeExists("error"));

        mvc.perform(post("/ui/orders/" + id + "/status").param("status", "specimen_collected").with(csrf()));
        mvc.perform(get("/ui/orders")).andExpect(content().string(containsString("Wprowadz wynik: MORF")));
        mvc.perform(post("/ui/orders/" + id + "/results/MORF").param("status", "final").param("v_HGB", "14,2")
                        .param("f_HGB", "H").param("performer", "Laborant UI").with(csrf()))
                .andExpect(redirectedUrl("/ui/orders")).andExpect(flash().attributeExists("message"));

        // zakonczone zlecenie jest w historii
        mvc.perform(get("/ui/orders").param("view", "history"))
                .andExpect(content().string(containsString("HGB: 14.2")))
                .andExpect(content().string(containsString("[H]")));
        mvc.perform(get("/fhir/ServiceRequest/" + id)).andExpect(content().string(containsString("\"completed\"")));
    }

    @Test
    void resultFormRejectsEmptyAndInvalidInput() throws Exception {
        String id = createViaFhir();
        mvc.perform(post("/ui/orders/" + id + "/status").param("status", "specimen_collected").with(csrf()));
        mvc.perform(post("/ui/orders/" + id + "/results/MORF").param("status", "final").with(csrf()))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/ui/orders/" + id + "/results/MORF").param("status", "final").param("v_HGB", "1")
                        .param("f_HGB", "ZZ").with(csrf()))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/ui/orders/" + id + "/results/MORF").param("status", "zly").param("v_HGB", "1")
                        .with(csrf()))
                .andExpect(flash().attributeExists("error"));
    }
}
