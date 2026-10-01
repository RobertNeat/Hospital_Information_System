package robert_neat.his_backend.terminology.snomed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import robert_neat.his_backend.security.HisUserPrincipal;
import robert_neat.his_backend.security.SecurityConfig;
import robert_neat.his_backend.staff.StaffRole;

@WebMvcTest(TerminologyController.class)
@Import(SecurityConfig.class)
class TerminologyControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SnowstormClient client;

    @MockitoBean
    private TerminologySuggestionService suggestions;

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

    private static RequestPostProcessor hisUser(UUID staffId) {
        HisUserPrincipal p = new HisUserPrincipal(UUID.randomUUID(), staffId, "EMP-1", StaffRole.DOCTOR,
                UUID.randomUUID());
        return authentication(UsernamePasswordAuthenticationToken.authenticated(p, "n/a", List.of()));
    }

    @Test
    void suggestionsUseStaffIdOfLoggedInDoctor() throws Exception {
        UUID staffId = UUID.randomUUID();
        when(suggestions.suggest(staffId, TerminologyKind.PROCEDURE, "rtg", 7)).thenReturn(
                new SnomedConceptPage(1, 0, List.of(new SnomedConcept("363679005", "Obrazowanie"))));

        mvc.perform(get("/api/v1/terminology/snomed/suggestions")
                        .param("kind", "procedure").param("term", "rtg").param("size", "7").with(hisUser(staffId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concepts[0].code").value("363679005"));
    }

    @Test
    void suggestionsDefaultSizeAndNonHisPrincipalUsesNullStaff() throws Exception {
        when(suggestions.suggest(null, TerminologyKind.DIAGNOSIS, null, 20))
                .thenReturn(new SnomedConceptPage(0, 0, List.of()));

        mvc.perform(get("/api/v1/terminology/snomed/suggestions").param("kind", "DIAGNOSIS").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void suggestionsInvalidOrMissingKindIs400() throws Exception {
        mvc.perform(get("/api/v1/terminology/snomed/suggestions").param("kind", "drug"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/terminology/snomed/suggestions"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(suggestions);
    }

    @Test
    @WithMockUser
    void suggestionsUnavailableIs503() throws Exception {
        when(suggestions.suggest(any(), any(), any(), anyInt()))
                .thenThrow(new TerminologyException.Unavailable("wylaczona", null));

        mvc.perform(get("/api/v1/terminology/snomed/suggestions").param("kind", "symptom"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser
    void suggestionsInvalidSizeFromServiceIs400() throws Exception {
        when(suggestions.suggest(any(), any(), any(), anyInt()))
                .thenThrow(new TerminologyException.InvalidRequest("limit", null));

        mvc.perform(get("/api/v1/terminology/snomed/suggestions").param("kind", "symptom").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suggestionsAnonymousIs401() throws Exception {
        mvc.perform(get("/api/v1/terminology/snomed/suggestions").param("kind", "symptom"))
                .andExpect(status().isUnauthorized());
    }
}
