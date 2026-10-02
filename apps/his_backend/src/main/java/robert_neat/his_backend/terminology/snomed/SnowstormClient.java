package robert_neat.his_backend.terminology.snomed;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import robert_neat.his_backend.ehr.Coding;
import robert_neat.his_backend.ehr.CodingSystem;

/**
 * Klient FHIR terminology servera (Snowstorm Lite). Polaczenie jest nawiazywane dopiero przy
 * pierwszym wywolaniu, wiec backend startuje takze bez dzialajacego Snowstorma.
 */
@Component
public class SnowstormClient {

    static final String SNOMED_SYSTEM = "http://snomed.info/sct";
    static final int MAX_ECL_LENGTH = 2000;
    static final int MAX_FILTER_LENGTH = 200;

    /** Refset "SNOMED CT to ICD-10 extended map" (zweryfikowany na lokalnym Snowstorm Lite 20261001). */
    static final String ICD10_MAP_FHIR_CM = "447562003";

    private final SnowstormProperties properties;
    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    public SnowstormClient(SnowstormProperties properties) {
        this(properties, buildRestClient(properties));
    }

    SnowstormClient(SnowstormProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(SnowstormProperties p) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(p.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(p.readTimeout());
        return RestClient.builder().baseUrl(p.baseUrl()).requestFactory(factory).build();
    }

    /** Prosta kontrola dostepnosci (zamiast HealthIndicator - brak actuatora): GET /metadata. */
    public boolean isAvailable() {
        if (!properties.enabled()) {
            return false;
        }
        try {
            restClient.get().uri("/metadata?_summary=true").accept(fhirJson()).retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public SnomedConceptPage expandEcl(String ecl, String filter, int count, int offset) {
        if (ecl == null || ecl.isBlank()) {
            throw new TerminologyException.InvalidRequest("ECL nie moze byc pusty", null);
        }
        if (ecl.length() > MAX_ECL_LENGTH) {
            throw new TerminologyException.InvalidRequest("ECL przekracza " + MAX_ECL_LENGTH + " znakow", null);
        }
        if (filter != null && filter.length() > MAX_FILTER_LENGTH) {
            throw new TerminologyException.InvalidRequest(
                    "Fraza wyszukiwania przekracza " + MAX_FILTER_LENGTH + " znakow", null);
        }
        if (count < 1 || count > properties.maxPageSize()) {
            throw new TerminologyException.InvalidRequest(
                    "limit musi byc w zakresie 1.." + properties.maxPageSize(), null);
        }
        if (offset < 0) {
            throw new TerminologyException.InvalidRequest("offset nie moze byc ujemny", null);
        }
        ensureEnabled();

        String valueSetUrl = SNOMED_SYSTEM + "?fhir_vs=ecl/" + ecl.strip();
        String body = execute("ECL", () -> restClient.get()
                .uri(b -> {
                    b.path("/ValueSet/$expand")
                            .queryParam("url", "{url}")
                            .queryParam("count", "{count}")
                            .queryParam("offset", "{offset}")
                            .queryParam("displayLanguage", "{lang}");
                    Map<String, Object> vars = new HashMap<>();
                    vars.put("url", valueSetUrl);
                    vars.put("count", count);
                    vars.put("offset", offset);
                    vars.put("lang", properties.displayLanguage());
                    if (filter != null && !filter.isBlank()) {
                        b.queryParam("filter", "{filter}");
                        vars.put("filter", filter.strip());
                    }
                    return b.build(vars);
                })
                .accept(fhirJson())
                .retrieve()
                .body(String.class));

        JsonNode expansion = parse(body).path("expansion");
        List<SnomedConcept> concepts = new ArrayList<>();
        for (JsonNode c : expansion.path("contains")) {
            String code = c.path("code").asString(null);
            if (code != null) {
                concepts.add(new SnomedConcept(code, c.path("display").asString(code)));
            }
        }
        return new SnomedConceptPage(expansion.path("total").asInt(concepts.size()), offset, List.copyOf(concepts));
    }

    // Snowstorm Lite 2.7.0 dla nieznanego SCTID na $lookup rzuca NPE w HAPI i odpowiada HTTP 500
    // (OperationOutcome, diagnostics zawiera "HAPI-0389" i "FHIRConcept.toHapi" - "concept" is null),
    // zamiast poprawnego 404. Rozpoznajemy ten konkretny, stabilny sygnal bledu i mapujemy go na 404,
    // zeby nie przykryc innych bledow 5xx (realna awaria/timeout Snowstorma) falszywym "not found".
    private static final String LOOKUP_UNKNOWN_CODE_SIGNATURE = "FHIRConcept.toHapi";

    public SnomedConcept lookup(String code) {
        if (code == null || !code.matches("\\d{6,18}")) {
            throw new TerminologyException.InvalidRequest("SCTID musi miec 6-18 cyfr", null);
        }
        ensureEnabled();
        String body;
        try {
            body = execute("lookup", () -> restClient.get()
                    .uri(b -> b.path("/CodeSystem/$lookup")
                            .queryParam("system", "{system}")
                            .queryParam("code", "{code}")
                            .queryParam("displayLanguage", "{lang}")
                            .build(Map.of("system", SNOMED_SYSTEM, "code", code,
                                    "lang", properties.displayLanguage())))
                    .accept(fhirJson())
                    .retrieve()
                    .body(String.class));
        } catch (TerminologyException.InvalidRequest e) {
            // 400/404/422 na lookup oznacza nieznany kod
            throw new TerminologyException.NotFound("Nie znaleziono pojecia SNOMED CT " + code);
        } catch (TerminologyException.Unavailable e) {
            if (isUnknownCodeServerError(e)) {
                throw new TerminologyException.NotFound("Nie znaleziono pojecia SNOMED CT " + code);
            }
            throw e;
        }
        String display = null;
        for (JsonNode p : parse(body).path("parameter")) {
            if ("display".equals(p.path("name").asString())) {
                display = p.path("valueString").asString(null);
            }
        }
        if (display == null) {
            throw new TerminologyException.NotFound("Nie znaleziono pojecia SNOMED CT " + code);
        }
        return new SnomedConcept(code, display);
    }

    /**
     * Dynamiczne tlumaczenie SCTID -> ICD-10 przez Snowstorm Lite (FHIR {@code ConceptMap/$translate}, refset
     * {@value #ICD10_MAP_FHIR_CM}). HIS nie przechowuje mapowan lokalnie - wynik jest liczony na zadanie,
     * przy eksporcie. Zweryfikowano na zywym Snowstorm Lite 2.7.0 (20261001): w przeciwienstwie do
     * {@code $lookup}, {@code $translate} zawsze odpowiada HTTP 200 (tez dla nieznanego SCTID) z
     * {@code {"name":"result","valueBoolean":false}} gdy brak mapowania - bez rozroznienia miedzy
     * "nieznany kod" a "znany kod bez mapowania ICD-10". Dlatego zwracamy po prostu puste, nie 404.
     */
    public List<Coding> translateToIcd10(String code) {
        if (code == null || !code.matches("\\d{6,18}")) {
            throw new TerminologyException.InvalidRequest("SCTID musi miec 6-18 cyfr", null);
        }
        ensureEnabled();
        String conceptMapUrl = SNOMED_SYSTEM + "?fhir_cm=" + ICD10_MAP_FHIR_CM;
        String body = execute("translate", () -> restClient.get()
                .uri(b -> b.path("/ConceptMap/$translate")
                        .queryParam("url", "{url}")
                        .queryParam("system", "{system}")
                        .queryParam("code", "{code}")
                        .build(Map.of("url", conceptMapUrl, "system", SNOMED_SYSTEM, "code", code)))
                .accept(fhirJson())
                .retrieve()
                .body(String.class));
        List<Coding> result = new ArrayList<>();
        for (JsonNode p : parse(body).path("parameter")) {
            if (!"match".equals(p.path("name").asString())) {
                continue;
            }
            for (JsonNode part : p.path("part")) {
                if (!"concept".equals(part.path("name").asString())) {
                    continue;
                }
                JsonNode coding = part.path("valueCoding");
                String icdCode = coding.path("code").asString(null);
                if (icdCode == null || icdCode.isBlank()) {
                    continue;
                }
                String display = coding.path("display").asString(icdCode);
                Coding translated = new Coding(CodingSystem.ICD_10, icdCode, display);
                if (!result.contains(translated)) {
                    result.add(translated);
                }
            }
        }
        return List.copyOf(result);
    }

    private void ensureEnabled() {
        if (!properties.enabled()) {
            throw new TerminologyException.Unavailable(
                    "Integracja z serwerem terminologii SNOMED CT jest wylaczona "
                            + "(his.terminology.snowstorm.enabled=false)", null);
        }
    }

    private static MediaType fhirJson() {
        return MediaType.valueOf("application/fhir+json");
    }

    /** True tylko dla HTTP 500 z cialem odpowiedzi noszacym sygnature nieznanego SCTID (patrz wyzej). */
    private static boolean isUnknownCodeServerError(TerminologyException.Unavailable e) {
        if (!(e.getCause() instanceof HttpServerErrorException httpError)) {
            return false;
        }
        if (httpError.getStatusCode().value() != 500) {
            return false;
        }
        String responseBody = httpError.getResponseBodyAsString();
        return responseBody != null && responseBody.contains(LOOKUP_UNKNOWN_CODE_SIGNATURE);
    }

    private String execute(String what, Supplier<String> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 400 || status == 404 || status == 422) {
                throw new TerminologyException.InvalidRequest(
                        "Serwer terminologii odrzucil zapytanie (" + what + ", HTTP " + status + ")", e);
            }
            throw new TerminologyException.Unavailable("Serwer terminologii zwrocil HTTP " + status, e);
        } catch (HttpServerErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 501) {
                throw new TerminologyException.InvalidRequest("Nieobslugiwana funkcja ECL (HTTP 501)", e);
            }
            throw new TerminologyException.Unavailable("Serwer terminologii zwrocil blad HTTP " + status, e);
        } catch (RestClientException e) {
            throw new TerminologyException.Unavailable("Serwer terminologii SNOMED CT jest niedostepny", e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return jsonMapper.readTree(body == null ? "{}" : body);
        } catch (JacksonException e) {
            throw new TerminologyException.Unavailable("Niepoprawna odpowiedz serwera terminologii", e);
        }
    }
}
