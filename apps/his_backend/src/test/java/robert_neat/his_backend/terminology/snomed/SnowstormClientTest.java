package robert_neat.his_backend.terminology.snomed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SnowstormClientTest {

    private static final String BASE = "http://snowstorm.test/fhir";
    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

    private MockRestServiceServer server;
    private SnowstormClient client;

    private static SnowstormProperties props(boolean enabled) {
        return new SnowstormProperties(enabled, BASE, Duration.ofSeconds(1), Duration.ofSeconds(1), "pl,en", 50);
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SnowstormClient(props(true), builder.build());
    }

    @Test
    void expandEncodesEclAndMapsExpansion() {
        String ecl = "<< 73211009 |Diabetes mellitus| : 363698007 = {<< 39057004}";
        server.expect(request -> {
                    String raw = request.getURI().getRawQuery();
                    assertThat(raw).contains("url=http%3A%2F%2Fsnomed.info%2Fsct%3Ffhir_vs%3Decl%2F%3C%3C%2073211009%20%7C"
                            + "Diabetes%20mellitus%7C%20%3A%20363698007%20%3D%20%7B%3C%3C%2039057004%7D");
                    assertThat(raw).contains("count=10").contains("offset=5")
                            .contains("displayLanguage=pl%2Cen").contains("filter=cukrzyca");
                    assertThat(request.getURI().getRawPath()).isEqualTo("/fhir/ValueSet/$expand");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"resourceType":"ValueSet","expansion":{"total":42,"offset":5,"contains":[
                          {"system":"http://snomed.info/sct","code":"73211009","display":"Cukrzyca"},
                          {"system":"http://snomed.info/sct","code":"44054006","display":"Cukrzyca typu 2"}]}}
                        """, FHIR_JSON));

        SnomedConceptPage page = client.expandEcl(ecl, "cukrzyca", 10, 5);

        assertThat(page.total()).isEqualTo(42);
        assertThat(page.offset()).isEqualTo(5);
        assertThat(page.concepts()).containsExactly(
                new SnomedConcept("73211009", "Cukrzyca"), new SnomedConcept("44054006", "Cukrzyca typu 2"));
        server.verify();
    }

    @Test
    void expandWithoutFilterOmitsFilterParam() {
        server.expect(request -> assertThat(request.getURI().getRawQuery()).doesNotContain("filter="))
                .andRespond(withSuccess("{\"resourceType\":\"ValueSet\",\"expansion\":{\"total\":0}}", FHIR_JSON));

        SnomedConceptPage page = client.expandEcl("<< 404684003", null, 10, 0);

        assertThat(page.total()).isZero();
        assertThat(page.concepts()).isEmpty();
    }

    @Test
    void expand400MapsToInvalidRequest() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/ValueSet/$expand")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.expandEcl("<<<", null, 10, 0))
                .isInstanceOf(TerminologyException.InvalidRequest.class);
    }

    @Test
    void expand501MapsToInvalidRequest() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/ValueSet/$expand")))
                .andRespond(withStatus(HttpStatus.NOT_IMPLEMENTED));

        assertThatThrownBy(() -> client.expandEcl("<< 1234567 {{ D term = \"x\" }}", null, 10, 0))
                .isInstanceOf(TerminologyException.InvalidRequest.class);
    }

    @Test
    void expand5xxMapsToUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/ValueSet/$expand")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.expandEcl("<< 404684003", null, 10, 0))
                .isInstanceOf(TerminologyException.Unavailable.class);
    }

    @Test
    void timeoutMapsToUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/ValueSet/$expand")))
                .andRespond(request -> {
                    throw new SocketTimeoutException("read timed out");
                });

        assertThatThrownBy(() -> client.expandEcl("<< 404684003", null, 10, 0))
                .isInstanceOf(TerminologyException.Unavailable.class);
    }

    @Test
    void validatesInputWithoutCallingServer() {
        assertThatThrownBy(() -> client.expandEcl(" ", null, 10, 0))
                .isInstanceOf(TerminologyException.InvalidRequest.class);
        assertThatThrownBy(() -> client.expandEcl("x".repeat(SnowstormClient.MAX_ECL_LENGTH + 1), null, 10, 0))
                .isInstanceOf(TerminologyException.InvalidRequest.class);
        assertThatThrownBy(() -> client.expandEcl("<< 1", null, 0, 0))
                .isInstanceOf(TerminologyException.InvalidRequest.class);
        assertThatThrownBy(() -> client.expandEcl("<< 1", null, 51, 0))
                .isInstanceOf(TerminologyException.InvalidRequest.class);
        assertThatThrownBy(() -> client.expandEcl("<< 1", null, 10, -1))
                .isInstanceOf(TerminologyException.InvalidRequest.class);
        assertThatThrownBy(() -> client.lookup("12ab"))
                .isInstanceOf(TerminologyException.InvalidRequest.class);
        server.verify();
    }

    @Test
    void disabledClientReportsUnavailable() {
        SnowstormClient disabled = new SnowstormClient(props(false), RestClient.builder().baseUrl(BASE).build());

        assertThatThrownBy(() -> disabled.expandEcl("<< 404684003", null, 10, 0))
                .isInstanceOf(TerminologyException.Unavailable.class)
                .hasMessageContaining("wylaczona");
        assertThatThrownBy(() -> disabled.lookup("73211009"))
                .isInstanceOf(TerminologyException.Unavailable.class);
        assertThat(disabled.isAvailable()).isFalse();
    }

    @Test
    void lookupMapsDisplay() {
        server.expect(request -> {
                    assertThat(request.getURI().getRawPath()).isEqualTo("/fhir/CodeSystem/$lookup");
                    assertThat(request.getURI().getRawQuery())
                            .contains("system=http%3A%2F%2Fsnomed.info%2Fsct").contains("code=73211009");
                })
                .andRespond(withSuccess("""
                        {"resourceType":"Parameters","parameter":[
                          {"name":"name","valueString":"SNOMED CT"},
                          {"name":"display","valueString":"Cukrzyca"}]}
                        """, FHIR_JSON));

        assertThat(client.lookup("73211009")).isEqualTo(new SnomedConcept("73211009", "Cukrzyca"));
    }

    @Test
    void lookupUnknownCodeMapsToNotFound() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/CodeSystem/$lookup")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.lookup("999999999"))
                .isInstanceOf(TerminologyException.NotFound.class);
    }

    // Snowstorm Lite 2.7.0 (zweryfikowane na realnej instancji) odpowiada HTTP 500 z tym cialem
    // dla nieznanego SCTID na $lookup, zamiast poprawnego 404.
    private static final String UNKNOWN_CODE_500_BODY = """
            {"resourceType":"OperationOutcome","issue":[{"severity":"error","code":"processing",
            "diagnostics":"HAPI-0389: Failed to call access method: java.lang.NullPointerException: \
            Cannot invoke \\"org.snomed.snowstormlite.domain.FHIRConcept.toHapi(org.snomed.snowstormlite.domain.\
            FHIRCodeSystem, org.snomed.snowstormlite.service.TermProvider, java.util.List)\\" because \\"concept\\" is null"}]}
            """;

    @Test
    void lookupUnknownCode500WithSnowstormLiteSignatureMapsToNotFound() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/CodeSystem/$lookup")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(UNKNOWN_CODE_500_BODY)
                        .contentType(FHIR_JSON));

        assertThatThrownBy(() -> client.lookup("999999999"))
                .isInstanceOf(TerminologyException.NotFound.class);
    }

    @Test
    void lookup500WithoutKnownSignatureStaysUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/CodeSystem/$lookup")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"diagnostics\":\"boom\"}]}")
                        .contentType(FHIR_JSON));

        assertThatThrownBy(() -> client.lookup("12345678"))
                .isInstanceOf(TerminologyException.Unavailable.class);
    }

    @Test
    void lookup503StaysUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/CodeSystem/$lookup")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.lookup("12345678"))
                .isInstanceOf(TerminologyException.Unavailable.class);
    }
}
