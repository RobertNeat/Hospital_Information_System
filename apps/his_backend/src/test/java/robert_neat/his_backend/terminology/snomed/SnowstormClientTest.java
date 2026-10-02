package robert_neat.his_backend.terminology.snomed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import robert_neat.his_backend.ehr.Coding;
import robert_neat.his_backend.ehr.CodingSystem;

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

    // --- translateToIcd10 ($translate, refset 447562003) ---

    // Ksztalt odpowiedzi ponizej zweryfikowany na zywym Snowstorm Lite 2.7.0 (20261001):
    // $translate (w przeciwienstwie do $lookup) nie zwraca "display" w valueCoding, a dla
    // nieznanego SCTID odpowiada HTTP 200 z {"result":false} - tak samo jak dla znanego kodu
    // bez mapowania ICD-10 (brak rozroznienia tych dwoch przypadkow po stronie serwera).

    @Test
    void translateMapsMatchedIcd10CodingFallingBackToCodeAsDisplay() {
        server.expect(request -> {
                    assertThat(request.getURI().getRawPath()).isEqualTo("/fhir/ConceptMap/$translate");
                    String raw = request.getURI().getRawQuery();
                    assertThat(raw).contains("url=http%3A%2F%2Fsnomed.info%2Fsct%3Ffhir_cm%3D447562003")
                            .contains("system=http%3A%2F%2Fsnomed.info%2Fsct").contains("code=38341003");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"resourceType":"Parameters","parameter":[
                          {"name":"result","valueBoolean":true},
                          {"name":"message","valueString":"Please observe the following map advice."},
                          {"name":"match","part":[
                            {"name":"equivalence","valueCode":"unmatched"},
                            {"name":"concept","valueCoding":{"system":"http://hl7.org/fhir/sid/icd-10","code":"I10"}},
                            {"name":"source","valueString":"http://snomed.info/sct/900000000000207008/version/20261001?fhir_cm=447562003"}]}]}
                        """, FHIR_JSON));

        List<Coding> result = client.translateToIcd10("38341003");

        assertThat(result).containsExactly(new Coding(CodingSystem.ICD_10, "I10", "I10"));
        server.verify();
    }

    @Test
    void translateWithMultipleMatchesReturnsAllDeduplicated() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/ConceptMap/$translate")))
                .andRespond(withSuccess("""
                        {"resourceType":"Parameters","parameter":[
                          {"name":"result","valueBoolean":true},
                          {"name":"match","part":[
                            {"name":"concept","valueCoding":{"code":"I21.9"}}]},
                          {"name":"match","part":[
                            {"name":"concept","valueCoding":{"code":"I21.9"}}]},
                          {"name":"match","part":[
                            {"name":"concept","valueCoding":{"code":"I21.0"}}]}]}
                        """, FHIR_JSON));

        List<Coding> result = client.translateToIcd10("22298006");

        assertThat(result).containsExactly(
                new Coding(CodingSystem.ICD_10, "I21.9", "I21.9"),
                new Coding(CodingSystem.ICD_10, "I21.0", "I21.0"));
    }

    @Test
    void translateNoMappingReturnsEmptyList() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/ConceptMap/$translate")))
                .andRespond(withSuccess("""
                        {"resourceType":"Parameters","parameter":[
                          {"name":"result","valueBoolean":false},
                          {"name":"message","valueString":"No mappings could be found for 138875005 (http://snomed.info/sct)"}]}
                        """, FHIR_JSON));

        assertThat(client.translateToIcd10("138875005")).isEmpty();
    }

    // Snowstorm Lite nie zglasza bledu dla nieznanego SCTID na $translate (inaczej niz $lookup) -
    // odpowiada HTTP 200 jak przy braku mapowania; traktujemy to tak samo, pustym wynikiem.
    @Test
    void translateUnknownCodeReturnsEmptyListLikeServerDoes() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/ConceptMap/$translate")))
                .andRespond(withSuccess("""
                        {"resourceType":"Parameters","parameter":[
                          {"name":"result","valueBoolean":false},
                          {"name":"message","valueString":"No mappings could be found for 999999999 (http://snomed.info/sct)"}]}
                        """, FHIR_JSON));

        assertThat(client.translateToIcd10("999999999")).isEmpty();
    }

    @Test
    void translate5xxMapsToUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/ConceptMap/$translate")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.translateToIcd10("38341003"))
                .isInstanceOf(TerminologyException.Unavailable.class);
    }

    @Test
    void translate400MapsToInvalidRequest() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/ConceptMap/$translate")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.translateToIcd10("38341003"))
                .isInstanceOf(TerminologyException.InvalidRequest.class);
    }

    @Test
    void translateValidatesSctidWithoutCallingServer() {
        assertThatThrownBy(() -> client.translateToIcd10("12ab"))
                .isInstanceOf(TerminologyException.InvalidRequest.class);
        server.verify();
    }

    @Test
    void translateDisabledClientReportsUnavailable() {
        SnowstormClient disabled = new SnowstormClient(props(false), RestClient.builder().baseUrl(BASE).build());

        assertThatThrownBy(() -> disabled.translateToIcd10("38341003"))
                .isInstanceOf(TerminologyException.Unavailable.class);
    }
}
