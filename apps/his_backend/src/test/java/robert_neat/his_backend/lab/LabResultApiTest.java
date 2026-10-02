package robert_neat.his_backend.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.security.StaffPrincipal;
import robert_neat.his_backend.lab.RecordLabResultCommand.ObservationInput;
import robert_neat.his_backend.lab.events.LabOrderStatusChanged;
import robert_neat.his_backend.lab.events.LabResultRecorded;

/**
 * Wyniki laboratoryjne (API.md, par. 4) na danych mock: 20 wynikow / 39 obserwacji, zaden nie potwierdzony;
 * 16 wynikow nieprawidlowych, w tym 5 krytycznych. Zapis wynikow ({@link LabResultRecordingService}) testowany
 * bezposrednio oraz przez natywny endpoint REST (`POST /lab-orders/{orderId}/results`). Tokeny z `POST /auth/login`;
 * aktor pochodzi z tokenu.
 */
@RecordApplicationEvents
class LabResultApiTest extends ApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private static final String KOWALSKI = "c078186c-c437-5fa8-8a5b-a6bf8883f8bf"; // 6x MORF
    private static final String DIABETIC = "a8500c41-1152-563d-b249-363417666099"; // 3x HBA1C, 2x GLU (wszystkie H)
    private static final String RENAL = "55cc6e9e-6413-58bc-88b6-6342579d8413"; // KREA/EGFR/NA/K, 3 krytyczne
    private static final String CARDIAC = "7466c824-06b6-57f2-8bae-2f20f77d64b6"; // TROP, CKMB (krytyczne)
    private static final String CRP_PATIENT = "0f0db024-a3c0-56a5-916d-3acf34a052e5"; // 1x CRP preliminary
    private static final String PREOP = "17a3dd05-d7d5-5211-ba70-0fc6e51cc476"; // MORF, INRPT (normy)
    private static final String SZYMANSKI = "50c8f3fa-ea66-581a-9207-f9c4c7131d26";

    private static final String RES_MORF_OLDEST = "e109ca60-f655-5616-bd02-cafe1ba29da4"; // z zlecenia, wszystko N
    private static final String RES_MORF_NO_ORDER = "9e8b58b7-dfa2-5c13-aec5-e4cd635d6c67"; // bez zlecenia
    private static final String RES_MORF_NEWEST = "8d783c14-9817-5ecd-958d-1115e98f3276";
    private static final String RES_TROP = "f581ba19-b12b-56a2-bc2d-f6f1b94b7bd2";
    private static final String RES_CRP = "0ea906ae-499c-52f7-8ce3-500b48d71c30";
    private static final String RES_ELEK = "cce215d2-6066-5197-933c-e37c30cbad1a";

    private static final String ORD_IN_PROGRESS = "4762fbd9-5f6c-516a-94fb-4b12d0ac3ada"; // MORF + CRP
    private static final String ITEM_IP_MORF = "250fcac6-148b-5e3b-84cb-982befddc613";
    private static final String ITEM_IP_CRP = "623216c8-e042-59d5-8858-b0896700c66b";
    private static final String ORD_ORDERED = "8651089d-9360-5c5d-b90c-2986ca095295"; // MORF + DDIMER
    private static final String ITEM_ORDERED_MORF = "153a6cb1-69c8-5149-8e4d-3c33283f2a46";
    private static final String ORD_SCHEDULED = "b6a0f537-bd2a-5dbe-b944-3786793db028"; // GLU
    private static final String ITEM_SCHEDULED_GLU = "7468aad3-aa5d-504a-8aff-8a05535e551b";
    private static final String ORD_COMPLETED = "c288e021-f72f-5590-b4ae-87c1ef6b6c08"; // MORF
    private static final String ITEM_COMPLETED_MORF = "b89404ab-9b9f-5a8d-8fc3-2e3420536fb6";

    private static final String DOCTOR_STAFF = "3bc5ba72-1a62-3681-ba58-fa6c83501852";
    private static final String LAB_STAFF = "353211f4-0d37-3369-8533-001c6946e7d7";
    private static final String IP_ORDERED_BY = "fdf3ef3c-c118-5896-aa53-c2eff5aa7a21";

    private static final Map<String, String> TOKENS = new HashMap<>();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager em;
    @Autowired
    private ApplicationEvents events;
    @Autowired
    private LabResultRecordingService recording;

    // --- uwierzytelnienie i uprawnienia ---

    @Test
    void everyEndpointWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/patients/{id}/lab-results", KOWALSKI)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patients/{id}/lab-results/trends/HGB", KOWALSKI)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patients/{id}/lab-results/analytes", KOWALSKI)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/lab-results")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/lab-results/{id}", RES_TROP)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/lab-results/{id}/acknowledge", RES_TROP).contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "nurse", "lab-tech", "admin"})
    void readRolesSeeResults(String login) throws Exception {
        as(login, get("/api/v1/patients/{id}/lab-results", KOWALSKI)).andExpect(status().isOk());
        as(login, get("/api/v1/patients/{id}/lab-results/trends/HGB", KOWALSKI)).andExpect(status().isOk());
        as(login, get("/api/v1/patients/{id}/lab-results/analytes", KOWALSKI)).andExpect(status().isOk());
        as(login, get("/api/v1/lab-results")).andExpect(status().isOk());
        as(login, get("/api/v1/lab-results/{id}", RES_TROP)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"radiologist", "pharmacist", "registrar"})
    void otherRolesCannotReadResults(String login) throws Exception {
        as(login, get("/api/v1/patients/{id}/lab-results", KOWALSKI)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as(login, get("/api/v1/patients/{id}/lab-results/trends/HGB", KOWALSKI)).andExpect(status().isForbidden());
        as(login, get("/api/v1/patients/{id}/lab-results/analytes", KOWALSKI)).andExpect(status().isForbidden());
        as(login, get("/api/v1/lab-results")).andExpect(status().isForbidden());
        as(login, get("/api/v1/lab-results/{id}", RES_TROP)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nurse", "lab-tech", "admin", "radiologist", "pharmacist", "registrar"})
    void onlyDoctorAcknowledges(String login) throws Exception {
        acknowledge(login, RES_TROP).andExpect(status().isForbidden());
        assertThat(reviewedAt(RES_TROP)).isNull();
    }

    // --- lista wynikow pacjenta ---

    @Test
    void patientListIsNewestFirstWithoutPaging() throws Exception {
        patientResults(KOWALSKI, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].id", contains(RES_MORF_NEWEST, "f5b4348c-c9e1-575b-993f-6012076bc903",
                        "5b2b6465-ec6f-5561-9f75-fc57e7d49939", "6b878afc-337c-5b5b-a4b7-cb9f57fbdf24",
                        RES_MORF_NO_ORDER, RES_MORF_OLDEST)));
        patientResults(CRP_PATIENT, null).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("preliminary"));
        patientResults(SZYMANSKI, null).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
    }

    @Test
    void patientListFilters() throws Exception {
        patientResults(KOWALSKI, "all").andExpect(jsonPath("$", hasSize(6)));
        patientResults(KOWALSKI, "abnormal").andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItems(RES_MORF_OLDEST, RES_MORF_NO_ORDER))));
        patientResults(KOWALSKI, "critical").andExpect(jsonPath("$", empty()));
        patientResults(PREOP, "abnormal").andExpect(jsonPath("$", empty()));
        patientResults(DIABETIC, "abnormal").andExpect(jsonPath("$", hasSize(5)));
        patientResults(DIABETIC, "critical").andExpect(jsonPath("$", empty()));
        patientResults(RENAL, "abnormal").andExpect(jsonPath("$", hasSize(4)));
        patientResults(RENAL, "critical").andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].testCode", containsInAnyOrder("ELEK", "KREA", "KREA")));
        patientResults(CARDIAC, "critical").andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void patientListRejectsBadFilterAndUnknownPatient() throws Exception {
        patientResults(KOWALSKI, "krytyczne").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("filter"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("critical")));
        patientResults(UUID.randomUUID().toString(), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        patientResults("to-nie-uuid", null).andExpect(status().isNotFound());
    }

    // --- szczegoly i kontrakt JSON ---

    @Test
    void detailContainsObservationsWithNumericValuesAndRanges() throws Exception {
        as("lab-tech", get("/api/v1/lab-results/{id}", RES_MORF_OLDEST)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RES_MORF_OLDEST))
                .andExpect(jsonPath("$.patientId").value(KOWALSKI))
                .andExpect(jsonPath("$.orderId").value(ORD_COMPLETED))
                .andExpect(jsonPath("$.orderItemId").value(ITEM_COMPLETED_MORF))
                .andExpect(jsonPath("$.testCode").value("MORF"))
                .andExpect(jsonPath("$.testName").value("Morfologia krwi z rozmazem"))
                .andExpect(jsonPath("$.category").value("hematology"))
                .andExpect(jsonPath("$.status").value("final"))
                .andExpect(jsonPath("$.performerName").value("lek. med. lab. Alicja Ostrowska"))
                .andExpect(jsonPath("$.collectedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.observations", hasSize(3)))
                .andExpect(jsonPath("$.observations[*].analyteCode", contains("HGB", "PLT", "WBC")))
                .andExpect(jsonPath("$.observations[?(@.analyteCode=='WBC')].value", contains(7.2)))
                .andExpect(jsonPath("$.observations[?(@.analyteCode=='WBC')].analyteName", contains("Leukocyty")))
                .andExpect(jsonPath("$.observations[?(@.analyteCode=='WBC')].unit", contains("tys/uL")))
                .andExpect(jsonPath("$.observations[?(@.analyteCode=='WBC')].referenceRange.low", contains(4)))
                .andExpect(jsonPath("$.observations[?(@.analyteCode=='WBC')].referenceRange.high", contains(10)))
                .andExpect(jsonPath("$.observations[?(@.analyteCode=='WBC')].flag", contains("N")));
    }

    @Test
    void detailKeepsStoredFlagsAndIntegerValues() throws Exception {
        as("doctor", get("/api/v1/lab-results/{id}", RES_TROP)).andExpect(status().isOk())
                .andExpect(jsonPath("$.observations[0].value").value(4520))
                .andExpect(jsonPath("$.observations[0].flag").value("HH"))
                .andExpect(jsonPath("$.observations[0].referenceRange.low").value(0))
                .andExpect(jsonPath("$.observations[0].referenceRange.high").value(14))
                .andExpect(jsonPath("$.comment").value("Wynik krytyczny, zgodny z ostrym zawałem serca."));
        as("doctor", get("/api/v1/lab-results/{id}", RES_ELEK)).andExpect(status().isOk())
                .andExpect(jsonPath("$.observations[?(@.analyteCode=='NA')].flag", contains("LL")))
                .andExpect(jsonPath("$.observations[?(@.analyteCode=='K')].flag", contains("HH")))
                .andExpect(jsonPath("$.observations[?(@.analyteCode=='K')].value", contains(6.3)));
    }

    @Test
    void jsonOmitsAbsentOptionalFieldsAndKeepsRequiredOnes() throws Exception {
        String withOrder = as("doctor", get("/api/v1/lab-results/{id}", RES_MORF_OLDEST)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> result = JsonPath.read(withOrder, "$");
        assertThat(result.keySet()).containsExactlyInAnyOrder("id", "patientId", "orderId", "orderItemId",
                "testCode", "testName", "category", "collectedAt", "resultedAt", "status", "observations",
                "performerName", "version"); // comment i reviewed* pomijane
        Map<String, Object> observation = JsonPath.read(withOrder, "$.observations[0]");
        assertThat(observation.keySet()).containsExactlyInAnyOrder("analyteCode", "analyteName", "value", "unit",
                "referenceRange", "flag");
        Map<String, Object> range = JsonPath.read(withOrder, "$.observations[0].referenceRange");
        assertThat(range.keySet()).containsExactlyInAnyOrder("low", "high"); // `text` pomijany
        assertThat(JsonPath.<Object>read(withOrder, "$.observations[0].value")).isInstanceOf(Number.class);

        String noOrder = as("doctor", get("/api/v1/lab-results/{id}", RES_MORF_NO_ORDER)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> external = JsonPath.read(noOrder, "$");
        assertThat(external.keySet()).doesNotContain("orderId", "orderItemId", "comment", "reviewedAt",
                "reviewedById");
        String commented = as("doctor", get("/api/v1/lab-results/{id}", RES_ELEK)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<Map<String, Object>>read(commented, "$").keySet()).contains("comment");
    }

    @Test
    void detailReturnsSnapshotNotCurrentCatalog() throws Exception {
        jdbc.update("update lab_test set name = 'Zmieniona nazwa' where code = 'MORF'");
        as("doctor", get("/api/v1/lab-results/{id}", RES_MORF_OLDEST))
                .andExpect(jsonPath("$.testName").value("Morfologia krwi z rozmazem"));
    }

    @Test
    void detailOfUnknownResultIs404() throws Exception {
        as("doctor", get("/api/v1/lab-results/{id}", UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        as("doctor", get("/api/v1/lab-results/{id}", "to-nie-uuid")).andExpect(status().isNotFound());
    }

    // --- acknowledge ---

    @Test
    void acknowledgeStoresActorAndTimeFromToken() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        acknowledge("doctor", RES_TROP).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RES_TROP))
                .andExpect(jsonPath("$.reviewedById").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.reviewedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.observations", hasSize(1)));
        assertThat(reviewedAt(RES_TROP)).isAfter(before);
        assertThat(jdbc.queryForObject("select reviewed_by_id::text from lab_result where id = ?::uuid", String.class,
                RES_TROP)).isEqualTo(DOCTOR_STAFF);
        as("doctor", get("/api/v1/lab-results/{id}", RES_TROP)).andExpect(jsonPath("$.reviewedById").value(DOCTOR_STAFF));
    }

    @Test
    void acknowledgeIsIdempotentAndKeepsFirstAcknowledgement() throws Exception {
        String first = acknowledge("doctor", RES_TROP).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Instant firstAt = reviewedAt(RES_TROP);
        // ponowne potwierdzenie (rowniez przez innego lekarza) - 200 i bez zmian
        String second = acknowledge("doctor", RES_TROP).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String third = acknowledge("user", RES_TROP).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
        assertThat(reviewedAt(RES_TROP)).isEqualTo(firstAt);
        assertThat(jdbc.queryForObject("select reviewed_by_id::text from lab_result where id = ?::uuid", String.class,
                RES_TROP)).isEqualTo(DOCTOR_STAFF);
    }

    @Test
    void acknowledgeAcceptsOptionalBody() throws Exception {
        as("doctor", post("/api/v1/lab-results/{id}/acknowledge", RES_CRP)).andExpect(status().isOk());
        as("doctor", post("/api/v1/lab-results/{id}/acknowledge", RES_MORF_OLDEST).contentType(JSON)
                .content("{\"version\":0}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedAt").exists());
    }

    @Test
    void acknowledgeWithWrongVersionIs409AndDoesNotChangeResult() throws Exception {
        as("doctor", post("/api/v1/lab-results/{id}/acknowledge", RES_TROP).contentType(JSON)
                .content("{\"version\":7}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(reviewedAt(RES_TROP)).isNull();
    }

    @Test
    void acknowledgeWithMatchingVersionSucceedsAndBumpsVersion() throws Exception {
        as("doctor", post("/api/v1/lab-results/{id}/acknowledge", RES_TROP).contentType(JSON)
                .content("{\"version\":0}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        // ponowne potwierdzenie ze starym (teraz niezgodnym) numerem -> 409, mimo ze stan wyniku sie nie zmienia
        as("doctor", post("/api/v1/lab-results/{id}/acknowledge", RES_TROP).contentType(JSON)
                .content("{\"version\":0}")).andExpect(status().isConflict());
        // z aktualnym numerem - nadal 200, idempotentne
        as("doctor", post("/api/v1/lab-results/{id}/acknowledge", RES_TROP).contentType(JSON)
                .content("{\"version\":1}")).andExpect(status().isOk());
    }

    @Test
    void acknowledgeOfUnknownResultIs404() throws Exception {
        acknowledge("doctor", UUID.randomUUID().toString()).andExpect(status().isNotFound());
        acknowledge("doctor", "to-nie-uuid").andExpect(status().isNotFound());
    }

    // --- zapis wyniku przez REST (laborant) ---

    @Test
    void recordResultEndpointRequiresAuthenticationAndPermission() throws Exception {
        mvc.perform(post("/api/v1/lab-orders/{id}/results", ORD_IN_PROGRESS).contentType(JSON)
                .content(resultBody(ITEM_IP_MORF, "HGB"))).andExpect(status().isUnauthorized());
        for (String login : new String[] {"doctor", "nurse", "admin", "radiologist", "pharmacist", "registrar"}) {
            as(login, post("/api/v1/lab-orders/{id}/results", ORD_IN_PROGRESS).contentType(JSON)
                    .content(resultBody(ITEM_IP_MORF, "HGB"))).andExpect(status().isForbidden());
        }
    }

    @Test
    void labTechRecordsResultViaRestAndItIsVisible() throws Exception {
        String location = as("lab-tech", post("/api/v1/lab-orders/{id}/results", ORD_IN_PROGRESS).contentType(JSON)
                .content(resultBody(ITEM_IP_MORF, "HGB"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.testCode").value("MORF"))
                .andExpect(jsonPath("$.orderId").value(ORD_IN_PROGRESS))
                .andExpect(jsonPath("$.performerName").exists())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).startsWith("/api/v1/lab-results/");
        as("doctor", get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.testCode").value("MORF"));
    }

    @Test
    void recordResultEndpointUnknownOrOrderedOrderBehavesAsRecordingService() throws Exception {
        as("lab-tech", post("/api/v1/lab-orders/{id}/results", UUID.randomUUID()).contentType(JSON)
                .content(resultBody(null, "HGB"))).andExpect(status().isNotFound());
        as("lab-tech", post("/api/v1/lab-orders/{id}/results", "to-nie-uuid").contentType(JSON)
                .content(resultBody(null, "HGB"))).andExpect(status().isNotFound());
        // ORD_ORDERED: material nie pobrany -> 409
        as("lab-tech", post("/api/v1/lab-orders/{id}/results", ORD_ORDERED).contentType(JSON)
                .content(resultBody(ITEM_ORDERED_MORF, "HGB"))).andExpect(status().isConflict());
        // nieznany analit -> 422
        as("lab-tech", post("/api/v1/lab-orders/{id}/results", ORD_IN_PROGRESS).contentType(JSON)
                .content(resultBody(ITEM_IP_MORF, "NIEMA"))).andExpect(status().isUnprocessableContent());
    }

    private String resultBody(String orderItemId, String analyteCode) {
        Instant collected = Instant.now().minusSeconds(3600);
        Instant resulted = Instant.now();
        return "{"
                + "\"orderItemId\":" + (orderItemId == null ? "null" : "\"" + orderItemId + "\"") + ","
                + "\"testCode\":\"MORF\","
                + "\"collectedAt\":\"" + collected + "\","
                + "\"resultedAt\":\"" + resulted + "\","
                + "\"status\":\"final\","
                + "\"observations\":[{\"analyteCode\":\"" + analyteCode + "\",\"numericValue\":7.5}]"
                + "}";
    }

    // --- trendy i anality ---

    @Test
    void trendListsNumericPointsOldestFirstWithHeaderFromLatestPoint() throws Exception {
        as("doctor", get("/api/v1/patients/{id}/lab-results/trends/HGB", KOWALSKI)).andExpect(status().isOk())
                .andExpect(jsonPath("$.analyteCode").value("HGB"))
                .andExpect(jsonPath("$.analyteName").value("Hemoglobina"))
                .andExpect(jsonPath("$.unit").value("g/dL"))
                .andExpect(jsonPath("$.low").value(12))
                .andExpect(jsonPath("$.high").value(16))
                .andExpect(jsonPath("$.points", hasSize(6)))
                .andExpect(jsonPath("$.points[*].value", contains(13.8, 12.9, 11.8, 11.2, 10.9, 10.3)))
                .andExpect(jsonPath("$.points[*].flag", contains("N", "N", "L", "L", "L", "L")))
                .andExpect(jsonPath("$.points[0].at", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")));
        as("doctor", get("/api/v1/patients/{id}/lab-results/trends/TROP", CARDIAC))
                .andExpect(jsonPath("$.points", hasSize(1)))
                .andExpect(jsonPath("$.points[0].value").value(4520))
                .andExpect(jsonPath("$.points[0].flag").value("HH"));
    }

    @Test
    void trendIsPatientScopedAndEmptyWithoutData() throws Exception {
        // pacjent ma wyniki, ale nie tego analitu: naglowek z katalogu, brak punktow
        as("doctor", get("/api/v1/patients/{id}/lab-results/trends/WBC", CRP_PATIENT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.analyteName").value("Leukocyty"))
                .andExpect(jsonPath("$.unit").value("tys/uL"))
                .andExpect(jsonPath("$.low").value(4))
                .andExpect(jsonPath("$.points", empty()));
        // nieznany analit: kod jako nazwa, pusta jednostka, bez low/high
        String json = as("doctor", get("/api/v1/patients/{id}/lab-results/trends/NIEMA", KOWALSKI))
                .andExpect(status().isOk()).andExpect(jsonPath("$.analyteName").value("NIEMA"))
                .andExpect(jsonPath("$.unit").value("")).andExpect(jsonPath("$.points", empty())).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<Map<String, Object>>read(json, "$").keySet())
                .containsExactlyInAnyOrder("analyteCode", "analyteName", "unit", "points");
        as("doctor", get("/api/v1/patients/{id}/lab-results/trends/HGB", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void trendableAnalytesAreNumericAnalytesOfThePatient() throws Exception {
        as("doctor", get("/api/v1/patients/{id}/lab-results/analytes", KOWALSKI)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("HGB", "PLT", "WBC")))
                .andExpect(jsonPath("$[?(@.code=='HGB')].name", contains("Hemoglobina")));
        as("doctor", get("/api/v1/patients/{id}/lab-results/analytes", RENAL))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("KREA", "EGFR", "NA", "K")));
        as("doctor", get("/api/v1/patients/{id}/lab-results/analytes", SZYMANSKI)).andExpect(jsonPath("$", empty()));
        as("doctor", get("/api/v1/patients/{id}/lab-results/analytes", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // --- inbox ---

    @Test
    void inboxReturnsAllResultsNewestFirstWithPatientSummary() throws Exception {
        as("doctor", get("/api/v1/lab-results")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(20))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].id").value(RES_CRP))
                .andExpect(jsonPath("$.items[0].patientId").value(CRP_PATIENT))
                .andExpect(jsonPath("$.items[0].patient.id").value(CRP_PATIENT))
                .andExpect(jsonPath("$.items[0].patient.mrn").exists())
                .andExpect(jsonPath("$.items[0].patient.firstName").exists())
                .andExpect(jsonPath("$.items[0].observations", hasSize(1)))
                .andExpect(jsonPath("$.items[19].id").value(RES_MORF_OLDEST)); // wynik sprzed 8738 h
    }

    @Test
    void inboxFilters() throws Exception {
        inbox("filter=critical").andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.items[*].patient.id", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.in(List.of(RENAL, CARDIAC)))));
        inbox("filter=abnormal").andExpect(jsonPath("$.totalElements").value(16));
        inbox("filter=all").andExpect(jsonPath("$.totalElements").value(20));
        inbox("patientId=" + KOWALSKI).andExpect(jsonPath("$.totalElements").value(6));
        inbox("patientId=" + KOWALSKI + "&filter=abnormal").andExpect(jsonPath("$.totalElements").value(4));
        inbox("patientId=" + PREOP + "&filter=critical").andExpect(jsonPath("$.totalElements").value(0));
        inbox("patientId=" + UUID.randomUUID()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void inboxPaginationAndSorting() throws Exception {
        inbox("size=8&page=0").andExpect(jsonPath("$.items", hasSize(8))).andExpect(jsonPath("$.totalPages").value(3));
        inbox("size=8&page=2").andExpect(jsonPath("$.items", hasSize(4))).andExpect(jsonPath("$.page").value(2));
        inbox("sort=collectedAt,asc&size=2").andExpect(jsonPath("$.items[0].id").value(RES_MORF_OLDEST));
        inbox("sort=resultedAt,asc&size=1").andExpect(jsonPath("$.items[0].id").value(RES_MORF_OLDEST));
        inbox("sort=testCode,asc&size=1").andExpect(jsonPath("$.items[0].testCode").value("CKMB"));
        inbox("sort=status,asc").andExpect(status().isOk());
        inbox("sort=category,desc").andExpect(status().isOk());
    }

    @Test
    void inboxRejectsUnknownSortAndBadParameters() throws Exception {
        inbox("sort=performerName,asc").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        inbox("filter=wszystkie").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("filter"));
        inbox("patientId=nie-uuid").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("patientId"));
    }

    @Test
    void inboxShowsAcknowledgement() throws Exception {
        acknowledge("doctor", RES_CRP).andExpect(status().isOk());
        inbox("patientId=" + CRP_PATIENT).andExpect(jsonPath("$.items[0].reviewedById").value(DOCTOR_STAFF));
    }

    // --- zapis wyniku: flagi, snapshoty, zdarzenie ---

    @Test
    void recordResultComputesFlagsFromCatalogRangesAndStoresSnapshots() throws Exception {
        LabResultResponse r = recording.recordResult(cmd(CRP_PATIENT, null, null, "MORF", ResultStatus.FINAL,
                ObservationInput.numeric("WBC", new BigDecimal("12.5")), // > 10 -> H
                ObservationInput.numeric("HGB", new BigDecimal("11.9")), // < 12 -> L
                ObservationInput.numeric("PLT", new BigDecimal("400")), // granica -> N
                ObservationInput.numeric("RBC", new BigDecimal("4.8")))); // N

        assertThat(r.testName()).isEqualTo("Morfologia krwi z rozmazem");
        assertThat(r.category().wire()).isEqualTo("hematology");
        assertThat(r.orderId()).isNull();
        assertThat(r.reviewedAt()).isNull();
        assertThat(r.observations()).extracting(LabResultResponse.Observation::analyteCode)
                .containsExactly("HGB", "PLT", "RBC", "WBC"); // sort po kodzie
        Map<String, ObservationFlag> flags = new HashMap<>();
        r.observations().forEach(o -> flags.put(o.analyteCode(), o.flag()));
        assertThat(flags).containsEntry("WBC", ObservationFlag.H).containsEntry("HGB", ObservationFlag.L)
                .containsEntry("PLT", ObservationFlag.N).containsEntry("RBC", ObservationFlag.N);
        LabResultResponse.Observation wbc = r.observations().stream().filter(o -> o.analyteCode().equals("WBC"))
                .findFirst().orElseThrow();
        assertThat(wbc.analyteName()).isEqualTo("Leukocyty");
        assertThat(wbc.unit()).isEqualTo("tys/uL");
        assertThat(wbc.referenceRange().low()).isEqualByComparingTo("4");
        assertThat(wbc.referenceRange().high()).isEqualByComparingTo("10");
        assertThat(wbc.value()).isEqualTo(new BigDecimal("12.5"));

        assertThat(events.stream(LabResultRecorded.class)).singleElement().satisfies(e -> {
            assertThat(e.resultId()).isEqualTo(r.id());
            assertThat(e.patientId()).isEqualTo(UUID.fromString(CRP_PATIENT));
            assertThat(e.critical()).isFalse();
            assertThat(e.criticalAnalyteCodes()).isEmpty();
            assertThat(e.orderId()).isNull();
            assertThat(e.orderedById()).isNull();
            assertThat(e.status()).isEqualTo(ResultStatus.FINAL);
            assertThat(e.testCode()).isEqualTo("MORF");
        });
        assertThat(events.stream(LabOrderStatusChanged.class)).isEmpty();

        // widoczny w API (lista pacjenta, trend, anality, inbox)
        patientResults(CRP_PATIENT, "abnormal").andExpect(jsonPath("$", hasSize(2)));
        as("doctor", get("/api/v1/patients/{id}/lab-results/trends/WBC", CRP_PATIENT))
                .andExpect(jsonPath("$.points", hasSize(1))).andExpect(jsonPath("$.points[0].flag").value("H"))
                .andExpect(jsonPath("$.points[0].value").value(12.5));
        inbox("patientId=" + CRP_PATIENT).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void explicitCriticalFlagMarksEventCritical() throws Exception {
        LabResultResponse r = recording.recordResult(cmd(RENAL, null, null, "KREA", ResultStatus.FINAL,
                ObservationInput.numeric("KREA", new BigDecimal("3.1")), // z zakresu: H
                ObservationInput.numeric("EGFR", new BigDecimal("15"), ObservationFlag.LL))); // jawnie krytyczny

        assertThat(r.observations()).extracting(LabResultResponse.Observation::flag)
                .containsExactly(ObservationFlag.LL, ObservationFlag.H); // EGFR, KREA (sort po kodzie)
        assertThat(events.stream(LabResultRecorded.class)).singleElement().satisfies(e -> {
            assertThat(e.critical()).isTrue();
            assertThat(e.criticalAnalyteCodes()).containsExactly("EGFR");
        });
        patientResults(RENAL, "critical").andExpect(jsonPath("$", hasSize(4))); // 3 z mock + nowy
    }

    @Test
    void textObservationIsStoredAsStringWithEmptyReferenceRange() throws Exception {
        LabResultResponse r = recording.recordResult(cmd(PREOP, null, null, "HISTPAT", ResultStatus.PRELIMINARY,
                ObservationInput.text("HISTPAT", "  Brak cech zlosliwosci.  ", ObservationFlag.A)));
        em.flush();
        em.clear(); // odczyt z bazy: zakres bez kolumn -> embedded null

        as("doctor", get("/api/v1/lab-results/{id}", r.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("pathology"))
                .andExpect(jsonPath("$.status").value("preliminary"))
                .andExpect(jsonPath("$.observations[0].value").value("Brak cech zlosliwosci."))
                .andExpect(jsonPath("$.observations[0].flag").value("A"))
                .andExpect(jsonPath("$.observations[0].unit").value(""))
                .andExpect(jsonPath("$.observations[0].referenceRange").isMap())
                .andExpect(jsonPath("$.observations[0].referenceRange.low").doesNotExist());
        String json = as("doctor", get("/api/v1/lab-results/{id}", r.id())).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<Object>read(json, "$.observations[0].value")).isInstanceOf(String.class);
        // wartosc tekstowa nie jest punktem trendu ani analitem do wykresu
        as("doctor", get("/api/v1/patients/{id}/lab-results/analytes", PREOP))
                .andExpect(jsonPath("$[*].code", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("HISTPAT"))));
        as("doctor", get("/api/v1/patients/{id}/lab-results/trends/HISTPAT", PREOP))
                .andExpect(jsonPath("$.points", empty()));
    }

    @Test
    void performerNameFallsBackToActorAndEventCarriesActor() {
        String expected = jdbc.queryForObject(
                "select trim(title || ' ' || first_name || ' ' || last_name) from staff_member where id = ?::uuid",
                String.class, LAB_STAFF);
        UUID actor = UUID.fromString(LAB_STAFF);
        StaffPrincipal principal = () -> actor;
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        try {
            LabResultResponse r = recording.recordResult(new RecordLabResultCommand(UUID.fromString(CRP_PATIENT), null, null, "CRP",
                    Instant.now().minus(3, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS),
                    ResultStatus.FINAL, " ", "  uwaga  ", List.of(ObservationInput.numeric("CRP", BigDecimal.TEN))));
            assertThat(r.performerName()).isEqualTo(expected);
            assertThat(r.comment()).isEqualTo("uwaga");
            assertThat(events.stream(LabResultRecorded.class)).singleElement()
                    .satisfies(e -> assertThat(e.actorId()).isEqualTo(actor));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // --- zapis wyniku: zlecenie i auto-completed ---

    @Test
    void lastFinalResultCompletesOrderWithHistoryAndEvent() throws Exception {
        // pozycja MORF wskazana zleceniem + kodem badania: zlecenie jeszcze nie konczy sie (CRP bez wyniku)
        LabResultResponse morf = recording.recordResult(cmd(CRP_PATIENT, ORD_IN_PROGRESS, null, "MORF",
                ResultStatus.FINAL, ObservationInput.numeric("HGB", new BigDecimal("14"))));
        assertThat(morf.orderId()).isEqualTo(UUID.fromString(ORD_IN_PROGRESS));
        assertThat(morf.orderItemId()).isEqualTo(UUID.fromString(ITEM_IP_MORF));
        assertThat(orderStatus(ORD_IN_PROGRESS)).isEqualTo("in_progress");
        assertThat(events.stream(LabOrderStatusChanged.class)).isEmpty();

        // druga pozycja wskazana tylko pozycja: zlecenie przechodzi do completed
        LabResultResponse crp = recording.recordResult(cmd(CRP_PATIENT, null, ITEM_IP_CRP, "CRP", ResultStatus.FINAL,
                ObservationInput.numeric("CRP", new BigDecimal("2"))));
        assertThat(crp.orderId()).isEqualTo(UUID.fromString(ORD_IN_PROGRESS));

        as("doctor", get("/api/v1/lab-orders/{id}", ORD_IN_PROGRESS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.statusHistory[*].status",
                        contains("ordered", "specimen_collected", "in_progress", "completed")))
                .andExpect(jsonPath("$.statusHistory[3].note").value(containsString("automatycznie")))
                .andExpect(jsonPath("$.statusHistory[3].byId").doesNotExist()) // aktor systemowy
                .andExpect(jsonPath("$.version").value(1));
        assertThat(events.stream(LabOrderStatusChanged.class)).singleElement().satisfies(e -> {
            assertThat(e.orderId()).isEqualTo(UUID.fromString(ORD_IN_PROGRESS));
            assertThat(e.previousStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
            assertThat(e.status()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(e.orderedById()).isEqualTo(UUID.fromString(IP_ORDERED_BY));
            assertThat(e.actorId()).isNull();
        });
        assertThat(events.stream(LabResultRecorded.class)).hasSize(2).last()
                .satisfies(e -> assertThat(e.orderedById()).isEqualTo(UUID.fromString(IP_ORDERED_BY)));
    }

    @Test
    void preliminaryResultDoesNotCompleteOrderUntilFinal() {
        recording.recordResult(cmd(CRP_PATIENT, ORD_IN_PROGRESS, null, "MORF", ResultStatus.FINAL,
                ObservationInput.numeric("HGB", new BigDecimal("14"))));
        recording.recordResult(cmd(CRP_PATIENT, ORD_IN_PROGRESS, null, "CRP", ResultStatus.PRELIMINARY,
                ObservationInput.numeric("CRP", new BigDecimal("2"))));
        assertThat(orderStatus(ORD_IN_PROGRESS)).isEqualTo("in_progress");
        assertThat(events.stream(LabOrderStatusChanged.class)).isEmpty();

        recording.recordResult(cmd(CRP_PATIENT, ORD_IN_PROGRESS, null, "CRP", ResultStatus.FINAL,
                ObservationInput.numeric("CRP", new BigDecimal("2"))));
        assertThat(orderStatus(ORD_IN_PROGRESS)).isEqualTo("completed");
        assertThat(events.stream(LabOrderStatusChanged.class)).hasSize(1);
    }

    @Test
    void externalResultNeverTouchesOrders() {
        recording.recordResult(cmd(CRP_PATIENT, null, null, "CRP", ResultStatus.FINAL,
                ObservationInput.numeric("CRP", new BigDecimal("2"))));
        assertThat(orderStatus(ORD_IN_PROGRESS)).isEqualTo("in_progress");
    }

    @Test
    void resultsAreRejectedForOrdersThatCannotAcceptThem() {
        // ordered / scheduled (brak pobranego materialu) -> 409
        assertThatThrownBy(() -> recording.recordResult(cmd(SZYMANSKI, ORD_ORDERED, null, "MORF", ResultStatus.FINAL,
                ObservationInput.numeric("HGB", BigDecimal.TEN)))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("ordered");
        assertThatThrownBy(() -> recording.recordResult(cmd(DIABETIC, ORD_SCHEDULED, null, "GLU", ResultStatus.FINAL,
                ObservationInput.numeric("GLU", BigDecimal.TEN)))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("scheduled");
        // cancelled
        jdbc.update("update lab_order set status = 'cancelled' where id = ?::uuid", ORD_IN_PROGRESS);
        assertThatThrownBy(() -> recording.recordResult(cmd(CRP_PATIENT, ORD_IN_PROGRESS, null, "MORF",
                ResultStatus.FINAL, ObservationInput.numeric("HGB", BigDecimal.TEN))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("cancelled");
        // completed: tylko korekta
        assertThatThrownBy(() -> recording.recordResult(cmd(KOWALSKI, ORD_COMPLETED, null, "MORF",
                ResultStatus.PRELIMINARY, ObservationInput.numeric("HGB", BigDecimal.TEN))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("completed");
        assertThat(events.stream(LabResultRecorded.class)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from lab_result", Integer.class)).isEqualTo(20);
    }

    @Test
    void correctionIsAllowedForCompletedOrderAndDoesNotChangeStatus() {
        LabResultResponse r = recording.recordResult(cmd(KOWALSKI, ORD_COMPLETED, null, "MORF",
                ResultStatus.CORRECTED, ObservationInput.numeric("HGB", new BigDecimal("10.5"))));
        assertThat(r.status()).isEqualTo(ResultStatus.CORRECTED);
        assertThat(orderStatus(ORD_COMPLETED)).isEqualTo("completed");
        assertThat(events.stream(LabOrderStatusChanged.class)).isEmpty();
        assertThat(events.stream(LabResultRecorded.class)).hasSize(1);
    }

    @Test
    void itemWithFinalResultAcceptsOnlyCorrections() {
        recording.recordResult(cmd(CRP_PATIENT, ORD_IN_PROGRESS, null, "MORF", ResultStatus.FINAL,
                ObservationInput.numeric("HGB", new BigDecimal("14"))));
        assertThatThrownBy(() -> recording.recordResult(cmd(CRP_PATIENT, ORD_IN_PROGRESS, null, "MORF",
                ResultStatus.FINAL, ObservationInput.numeric("HGB", new BigDecimal("13")))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> recording.recordResult(cmd(CRP_PATIENT, ORD_IN_PROGRESS, null, "MORF",
                ResultStatus.PRELIMINARY, ObservationInput.numeric("HGB", new BigDecimal("13")))))
                .isInstanceOf(ConflictException.class);
        recording.recordResult(cmd(CRP_PATIENT, ORD_IN_PROGRESS, null, "MORF", ResultStatus.CORRECTED,
                ObservationInput.numeric("HGB", new BigDecimal("13"))));
        assertThat(events.stream(LabResultRecorded.class)).hasSize(2);
    }

    // --- zapis wyniku: walidacja 422 ---

    @Test
    void recordResultValidatesReferencesAndConsistency() {
        // nieznany pacjent / badanie / zlecenie / pozycja
        assertFields(cmd(UUID.randomUUID().toString(), null, null, "MORF", ResultStatus.FINAL, obs("HGB")),
                "patientId");
        assertFields(cmd(CRP_PATIENT, null, null, "NIEMA", ResultStatus.FINAL, obs("HGB")), "testCode");
        assertFields(cmd(CRP_PATIENT, UUID.randomUUID().toString(), null, "MORF", ResultStatus.FINAL, obs("HGB")),
                "orderId");
        assertFields(cmd(CRP_PATIENT, null, UUID.randomUUID().toString(), "MORF", ResultStatus.FINAL, obs("HGB")),
                "orderItemId");
        // zlecenie innego pacjenta, inne badanie niz pozycja, pozycja spoza zlecenia, badania nie ma na zleceniu
        assertFields(cmd(KOWALSKI, ORD_IN_PROGRESS, null, "MORF", ResultStatus.FINAL, obs("HGB")), "orderId");
        assertFields(cmd(CRP_PATIENT, null, ITEM_IP_CRP, "MORF", ResultStatus.FINAL, obs("HGB")), "testCode");
        assertFields(cmd(CRP_PATIENT, ORD_ORDERED, ITEM_IP_MORF, "MORF", ResultStatus.FINAL, obs("HGB")),
                "orderItemId");
        assertFields(cmd(CRP_PATIENT, ORD_IN_PROGRESS, null, "GLU", ResultStatus.FINAL,
                ObservationInput.numeric("GLU", BigDecimal.TEN)), "testCode");
        assertThat(jdbc.queryForObject("select count(*) from lab_result", Integer.class)).isEqualTo(20);
        assertThat(events.stream(LabResultRecorded.class)).isEmpty();
    }

    @Test
    void recordResultValidatesObservationsAndDates() {
        // analit spoza badania, duplikat, brak wartosci, obie wartosci, poza zakresem kolumny
        assertFields(cmd(CRP_PATIENT, null, null, "MORF", ResultStatus.FINAL, obs("CRP")),
                "observations[0].analyteCode");
        assertFields(cmd(CRP_PATIENT, null, null, "MORF", ResultStatus.FINAL, obs("HGB"), obs("HGB")),
                "observations[1].analyteCode");
        assertFields(cmd(CRP_PATIENT, null, null, "MORF", ResultStatus.FINAL,
                new ObservationInput("HGB", null, null, null)), "observations[0].value");
        assertFields(cmd(CRP_PATIENT, null, null, "MORF", ResultStatus.FINAL,
                new ObservationInput("HGB", BigDecimal.ONE, "tekst", null)), "observations[0].value");
        assertFields(cmd(CRP_PATIENT, null, null, "MORF", ResultStatus.FINAL,
                ObservationInput.numeric("HGB", new BigDecimal("1E12"))), "observations[0].value");
        assertFields(cmd(CRP_PATIENT, null, null, "MORF", ResultStatus.FINAL), "observations");
        // daty i pola wymagane
        Instant now = Instant.now();
        assertFields(new RecordLabResultCommand(UUID.fromString(CRP_PATIENT), null, null, "MORF", now, now.minusSeconds(60),
                ResultStatus.FINAL, "Lab", null, List.of(obs("HGB"))), "resultedAt");
        assertFields(new RecordLabResultCommand(null, null, null, " ", null, null, null, "Lab", null, null),
                "patientId", "testCode", "collectedAt", "resultedAt", "status", "observations");
        // brak wykonawcy i brak sesji
        assertThatThrownBy(() -> recording.recordResult(new RecordLabResultCommand(UUID.fromString(CRP_PATIENT), null, null, "MORF",
                now.minusSeconds(60), now, ResultStatus.FINAL, null, null, List.of(obs("HGB")))))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> assertThat(((ValidationFailedException) e).getErrors())
                        .extracting(f -> f.field()).containsExactly("performerName"));
        assertThat(events.stream(LabResultRecorded.class)).isEmpty();
    }

    // --- pomocnicze ---

    private void assertFields(RecordLabResultCommand command, String... fields) {
        assertThatThrownBy(() -> recording.recordResult(command)).isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> assertThat(((ValidationFailedException) e).getErrors())
                        .extracting(f -> f.field()).containsExactlyInAnyOrder(fields));
    }

    private static ObservationInput obs(String analyte) {
        return ObservationInput.numeric(analyte, BigDecimal.TEN);
    }

    private static RecordLabResultCommand cmd(String patientId, String orderId, String itemId, String testCode,
            ResultStatus status, ObservationInput... observations) {
        Instant now = Instant.now();
        return new RecordLabResultCommand(UUID.fromString(patientId), orderId == null ? null : UUID.fromString(orderId),
                itemId == null ? null : UUID.fromString(itemId), testCode, now.minus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.HOURS), status, "lek. med. lab. Test", null, List.of(observations));
    }

    private String orderStatus(String orderId) {
        return jdbc.queryForObject("select status from lab_order where id = ?::uuid", String.class, orderId);
    }

    private Instant reviewedAt(String resultId) {
        java.sql.Timestamp ts = jdbc.queryForObject("select reviewed_at from lab_result where id = ?::uuid",
                java.sql.Timestamp.class, resultId);
        return ts == null ? null : ts.toInstant();
    }

    private ResultActions patientResults(String patientId, String filter) throws Exception {
        return as("doctor", get("/api/v1/patients/{id}/lab-results" + (filter == null ? "" : "?filter=" + filter),
                patientId));
    }

    private ResultActions inbox(String query) throws Exception {
        return as("doctor", get("/api/v1/lab-results?" + query));
    }

    private ResultActions acknowledge(String login, String resultId) throws Exception {
        return as(login, post("/api/v1/lab-results/{id}/acknowledge", resultId).contentType(JSON).content("{}"));
    }

    private ResultActions as(String login,
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(login)));
    }

    private String bearer(String login) throws Exception {
        String token = TOKENS.get(login);
        if (token == null) {
            String response = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                    .content("{\"employeeId\":\"" + login + "\",\"password\":\"" + login + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            token = JsonPath.read(response, "$.accessToken");
            TOKENS.put(login, token);
        }
        return "Bearer " + token;
    }
}
