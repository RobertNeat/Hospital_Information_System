package robert_neat.his_backend.terminology.snomed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import robert_neat.his_backend.security.SecurityConfig;

@WebMvcTest(TerminologyController.class)
@Import(SecurityConfig.class)
class TerminologyControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SnowstormClient client;

    @Test
    @WithMockUser
    void searchReturnsPage() throws Exception {
        when(client.expandEcl("<< 404684003", "ast", 5, 0)).thenReturn(
                new SnomedConceptPage(1, 0, List.of(new SnomedConcept("195967001", "Astma"))));

        mvc.perform(get("/api/v1/terminology/snomed/concepts")
                        .param("ecl", "<< 404684003").param("term", "ast").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.concepts[0].code").value("195967001"))
                .andExpect(jsonPath("$.concepts[0].display").value("Astma"));
    }

    @Test
    @WithMockUser
    void termOnlyUsesRootEcl() throws Exception {
        when(client.expandEcl(eq(TerminologyController.ALL_CONCEPTS_ECL), eq("ast"), anyInt(), anyInt()))
                .thenReturn(new SnomedConceptPage(0, 0, List.of()));

        mvc.perform(get("/api/v1/terminology/snomed/concepts").param("term", "ast"))
                .andExpect(status().isOk());
        verify(client).expandEcl(TerminologyController.ALL_CONCEPTS_ECL, "ast", 20, 0);
    }

    @Test
    @WithMockUser
    void missingEclAndTermIsBadRequestProblem() throws Exception {
        mvc.perform(get("/api/v1/terminology/snomed/concepts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists());
        verifyNoInteractions(client);
    }

    @Test
    @WithMockUser
    void invalidEclFromServerIsBadRequest() throws Exception {
        when(client.expandEcl(any(), any(), anyInt(), anyInt()))
                .thenThrow(new TerminologyException.InvalidRequest("zly ecl", null));

        mvc.perform(get("/api/v1/terminology/snomed/concepts").param("ecl", "<<<"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("zly ecl"));
    }

    @Test
    @WithMockUser
    void unavailableIs503Problem() throws Exception {
        when(client.expandEcl(any(), any(), anyInt(), anyInt()))
                .thenThrow(new TerminologyException.Unavailable("niedostepny", null));

        mvc.perform(get("/api/v1/terminology/snomed/concepts").param("ecl", "<< 1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("niedostepny"));
    }

    @Test
    @WithMockUser
    void lookupReturnsConcept() throws Exception {
        when(client.lookup("73211009")).thenReturn(new SnomedConcept("73211009", "Cukrzyca"));

        mvc.perform(get("/api/v1/terminology/snomed/concepts/73211009"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.display").value("Cukrzyca"));
    }

    @Test
    @WithMockUser
    void lookupInvalidSctidIs400() throws Exception {
        when(client.lookup("abc")).thenThrow(new TerminologyException.InvalidRequest("SCTID", null));

        mvc.perform(get("/api/v1/terminology/snomed/concepts/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void lookupUnknownIs404() throws Exception {
        when(client.lookup("999999")).thenThrow(new TerminologyException.NotFound("brak"));

        mvc.perform(get("/api/v1/terminology/snomed/concepts/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void jwtPrincipalIsAccepted() throws Exception {
        when(client.lookup("73211009")).thenReturn(new SnomedConcept("73211009", "Cukrzyca"));

        mvc.perform(get("/api/v1/terminology/snomed/concepts/73211009").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousIsUnauthorizedProblem() throws Exception {
        mvc.perform(get("/api/v1/terminology/snomed/concepts/73211009"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
