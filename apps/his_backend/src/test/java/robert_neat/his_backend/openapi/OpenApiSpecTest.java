package robert_neat.his_backend.openapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import robert_neat.his_backend.ApiIntegrationTest;

/**
 * Zywa specyfikacja OpenAPI (springdoc): dostepna bez logowania (polityka jak `/actuator/health/**`, LAN-wewnetrzny
 * system), ogranicza sie do `/api/**` (patrz `springdoc.paths-to-match`), `/fhir/**` (mTLS, HAPI) poza specyfikacja.
 */
class OpenApiSpecTest extends ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void apiDocsIsPublicAndListsRestApi() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/fhir/ServiceRequest/{id}']").doesNotExist());
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
