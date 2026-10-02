package robert_neat.his_backend.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.lab.events.LabOrderStatusChanged;

/**
 * Kontrakt zlecen laboratoryjnych (API.md, par. 4) na danych mock: 7 zlecen (4 completed, 1 in_progress,
 * 1 scheduled, 1 ordered; 3 routine, 1 urgent, 3 stat). Tokeny z `POST /auth/login`; aktor pochodzi z tokenu.
 */
@RecordApplicationEvents
class LabOrderApiTest extends ApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    // zlecenia mock
    private static final String ORD_COMPLETED_1 = "c288e021-f72f-5590-b4ae-87c1ef6b6c08"; // routine, MORF
    private static final String ORD_COMPLETED_2 = "fec0820a-7381-5b5c-aa10-e8a8812c27d0"; // stat, TROP + CKMB
    private static final String ORD_COMPLETED_3 = "3b52882d-dcb5-5d90-92c2-43b942f9a6a7"; // routine, 3 pozycje
    private static final String ORD_IN_PROGRESS = "4762fbd9-5f6c-516a-94fb-4b12d0ac3ada"; // stat, MORF + CRP
    private static final String ORD_COMPLETED_4 = "dc0fa407-bb9f-5041-ba51-a2eb747701b6"; // urgent, ELEK
    private static final String ORD_SCHEDULED = "b6a0f537-bd2a-5dbe-b944-3786793db028"; // routine, fasting, GLU
    private static final String ORD_ORDERED = "8651089d-9360-5c5d-b90c-2986ca095295"; // stat, MORF + DDIMER

    private static final String KOWALSKI = "c078186c-c437-5fa8-8a5b-a6bf8883f8bf";
    private static final String KOWALSKI_HOSPITALIZATION = "11b7050d-6f4c-546a-9bf7-da0f024b363e";
    private static final String WISNIEWSKA_ENCOUNTER = "2e790042-5ce2-543b-baee-416bf421230d";
    private static final String SZYMANSKI = "50c8f3fa-ea66-581a-9207-f9c4c7131d26";
    private static final String IN_PROGRESS_PATIENT = "0f0db024-a3c0-56a5-916d-3acf34a052e5";

    private static final String DOCTOR_STAFF = "3bc5ba72-1a62-3681-ba58-fa6c83501852";
    private static final String NURSE_STAFF = "28222254-25c0-34db-938f-9228b7a20c52";
    private static final String LAB_STAFF = "353211f4-0d37-3369-8533-001c6946e7d7";

    /** Dozwolone przejscia (poza `cancelled`, ktore idzie akcja /cancel) - niezaleznie od implementacji. */
    private static final Map<OrderStatus, List<OrderStatus>> ALLOWED = Map.of(
            OrderStatus.ORDERED, List.of(OrderStatus.SCHEDULED, OrderStatus.SPECIMEN_COLLECTED),
            OrderStatus.SCHEDULED, List.of(OrderStatus.SPECIMEN_COLLECTED),
            OrderStatus.SPECIMEN_COLLECTED, List.of(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED),
            OrderStatus.IN_PROGRESS, List.of(OrderStatus.COMPLETED),
            OrderStatus.COMPLETED, List.of(),
            OrderStatus.CANCELLED, List.of());

    private static final Map<String, String> TOKENS = new HashMap<>();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEvents events;

    // --- uwierzytelnienie i uprawnienia ---

    @Test
    void everyEndpointWithoutTokenIsUnauthorized() throws Exception {
        String body = "{}";
        mvc.perform(get("/api/v1/lab-orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/lab-orders/{id}", ORD_ORDERED)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/patients/{id}/lab-orders", KOWALSKI).contentType(JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/lab-orders/{id}/status", ORD_ORDERED).contentType(JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/lab-orders/{id}/cancel", ORD_ORDERED).contentType(JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "nurse", "lab-tech", "admin"})
    void readRolesSeeOrders(String login) throws Exception {
        as(login, get("/api/v1/lab-orders")).andExpect(status().isOk());
        as(login, get("/api/v1/lab-orders/{id}", ORD_ORDERED)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"radiologist", "pharmacist", "registrar"})
    void otherRolesCannotReadOrders(String login) throws Exception {
        as(login, get("/api/v1/lab-orders")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as(login, get("/api/v1/lab-orders/{id}", ORD_ORDERED)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nurse", "lab-tech", "admin", "radiologist", "pharmacist", "registrar"})
    void onlyDoctorCreatesAndCancels(String login) throws Exception {
        create(login, KOWALSKI, orderJson("MORF", "blood")).andExpect(status().isForbidden());
        cancel(login, ORD_ORDERED, "Powód").andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "radiologist", "pharmacist", "registrar"})
    void rolesWithoutStatusPermissionGet403(String login) throws Exception {
        postStatus(login, ORD_ORDERED, "scheduled").andExpect(status().isForbidden());
        postStatus(login, ORD_ORDERED, "specimen_collected").andExpect(status().isForbidden());
    }

    // --- worklista ---

    @Test
    void worklistReturnsAllOrdersNewestFirstWithPageEnvelope() throws Exception {
        as("doctor", get("/api/v1/lab-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(7)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[*].id", contains(ORD_IN_PROGRESS, ORD_ORDERED, ORD_COMPLETED_2,
                        ORD_COMPLETED_3, ORD_COMPLETED_1, ORD_SCHEDULED, ORD_COMPLETED_4)));
    }

    @Test
    void worklistFilters() throws Exception {
        list("status=completed").andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ORD_COMPLETED_1, ORD_COMPLETED_2,
                        ORD_COMPLETED_3, ORD_COMPLETED_4)));
        list("status=in_progress").andExpect(jsonPath("$.items[*].id", contains(ORD_IN_PROGRESS)));
        list("status=scheduled").andExpect(jsonPath("$.items[*].id", contains(ORD_SCHEDULED)));
        list("status=cancelled").andExpect(jsonPath("$.totalElements").value(0));
        list("urgency=stat").andExpect(jsonPath("$.items[*].id",
                containsInAnyOrder(ORD_COMPLETED_2, ORD_IN_PROGRESS, ORD_ORDERED)));
        list("urgency=urgent").andExpect(jsonPath("$.items[*].id", contains(ORD_COMPLETED_4)));
        list("urgency=routine").andExpect(jsonPath("$.totalElements").value(3));
        list("patientId=" + SZYMANSKI).andExpect(jsonPath("$.items[*].id", contains(ORD_ORDERED)));
        list("patientId=" + UUID.randomUUID()).andExpect(jsonPath("$.totalElements").value(0));
        list("status=completed&urgency=stat").andExpect(jsonPath("$.items[*].id", contains(ORD_COMPLETED_2)));
        list("status=ordered&urgency=routine").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void worklistFiltersByOrderedAtRange() throws Exception {
        Instant now = Instant.now();
        list("orderedFrom=" + now.minus(30, ChronoUnit.HOURS))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ORD_IN_PROGRESS, ORD_ORDERED, ORD_COMPLETED_2)));
        list("orderedTo=" + now.minus(100, ChronoUnit.HOURS))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ORD_SCHEDULED, ORD_COMPLETED_4)));
        list("orderedFrom=" + now.minus(60, ChronoUnit.HOURS) + "&orderedTo=" + now.minus(40, ChronoUnit.HOURS))
                .andExpect(jsonPath("$.items[*].id", contains(ORD_COMPLETED_3)));
        list("orderedFrom=wczoraj").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("orderedFrom"));
    }

    @Test
    void worklistPaginationAndSorting() throws Exception {
        list("size=3&page=0").andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(7)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].id").value(ORD_IN_PROGRESS));
        list("size=3&page=2").andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.items[0].id").value(ORD_COMPLETED_4));
        list("sort=orderedAt,asc&size=2").andExpect(jsonPath("$.items[*].id", contains(ORD_COMPLETED_4, ORD_SCHEDULED)));
        list("sort=plannedCollectionAt,desc&size=1").andExpect(jsonPath("$.items[0].id").value(ORD_IN_PROGRESS));
        list("sort=status,asc&status=completed").andExpect(status().isOk());
        list("sort=urgency,desc&size=1").andExpect(status().isOk());
    }

    @Test
    void worklistRejectsUnknownSortAndBadFilterValues() throws Exception {
        list("sort=clinicalInfo,asc").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        list("status=zakonczone").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("in_progress")));
        list("urgency=asap").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("urgency"));
        list("patientId=nie-uuid").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("patientId"));
    }

    // --- szczegoly i kontrakt JSON ---

    @Test
    void detailContainsStatusHistoryAndSnapshots() throws Exception {
        as("lab-tech", get("/api/v1/lab-orders/{id}", ORD_IN_PROGRESS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORD_IN_PROGRESS))
                .andExpect(jsonPath("$.patientId").value(IN_PROGRESS_PATIENT))
                .andExpect(jsonPath("$.orderedById").value("fdf3ef3c-c118-5896-aa53-c2eff5aa7a21"))
                .andExpect(jsonPath("$.urgency").value("stat"))
                .andExpect(jsonPath("$.fasting").value(false))
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andExpect(jsonPath("$.clinicalInfo").value("Ostry ból brzucha, podejrzenie zapalenia wyrostka robaczkowego."))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].testCode", containsInAnyOrder("MORF", "CRP")))
                .andExpect(jsonPath("$.items[?(@.testCode=='CRP')].testName", contains("CRP")))
                .andExpect(jsonPath("$.items[?(@.testCode=='CRP')].specimenType", contains("serum")))
                .andExpect(jsonPath("$.items[?(@.testCode=='MORF')].specimenType", contains("blood")))
                .andExpect(jsonPath("$.statusHistory[*].status", contains("ordered", "specimen_collected", "in_progress")))
                .andExpect(jsonPath("$.statusHistory[0].at", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void jsonOmitsAbsentOptionalFieldsAndKeepsRequiredOnes() throws Exception {
        String json = as("doctor", get("/api/v1/lab-orders/{id}", ORD_ORDERED)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> order = JsonPath.read(json, "$");
        assertThat(order.keySet()).contains("id", "patientId", "orderedById", "orderedAt", "items", "urgency",
                "fasting", "plannedCollectionAt", "clinicalInfo", "status", "statusHistory", "version", "createdAt",
                "updatedAt")
                .doesNotContain("encounterId", "diagnosisCode", "notes", "createdById", "updatedById");
        Map<String, Object> item = JsonPath.read(json, "$.items[0]");
        assertThat(item.keySet()).containsExactlyInAnyOrder("id", "testCode", "testName", "specimenType");
        Map<String, Object> change = JsonPath.read(json, "$.statusHistory[0]");
        assertThat(change.keySet()).containsExactlyInAnyOrder("status", "at"); // byId i note pomijane, gdy brak
    }

    @Test
    void detailReturnsSnapshotNotCurrentCatalog() throws Exception {
        jdbc.update("update lab_test set name = 'Zmieniona nazwa' where code = 'MORF'");
        as("doctor", get("/api/v1/lab-orders/{id}", ORD_ORDERED))
                .andExpect(jsonPath("$.items[?(@.testCode=='MORF')].testName", contains("Morfologia krwi z rozmazem")));
    }

    @Test
    void detailOfUnknownOrderIs404() throws Exception {
        as("doctor", get("/api/v1/lab-orders/{id}", UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        as("doctor", get("/api/v1/lab-orders/{id}", "to-nie-uuid")).andExpect(status().isNotFound());
    }

    // --- utworzenie ---

    @Test
    void createStoresOrderWithSnapshotInitialHistoryAndActorFromToken() throws Exception {
        jdbc.update("update lab_test set name = 'Nazwa w katalogu' where code = 'CRP'");
        String body = "{\"patientId\":\"" + KOWALSKI + "\",\"encounterId\":\"" + KOWALSKI_HOSPITALIZATION
                + "\",\"orderedById\":\"" + NURSE_STAFF + "\",\"urgency\":\"urgent\",\"fasting\":false,"
                + "\"plannedCollectionAt\":\"2030-01-02T08:00:00Z\","
                + "\"diagnosisCode\":{\"system\":\"SNOMED\",\"code\":\" 84114007 \",\"display\":\"Niewydolność serca\"},"
                + "\"clinicalInfo\":\"  Kontrola CRP.  \",\"notes\":\"Pobrać rano\","
                + "\"status\":\"completed\",\"version\":9,"
                + "\"items\":[{\"testCode\":\"CRP\",\"testName\":\"Nazwa od klienta\",\"specimenType\":\"blood\"},"
                + "{\"testCode\":\"MORF\",\"specimenType\":\"blood\"}]}";
        String response = create("doctor", KOWALSKI, body)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/v1/lab-orders/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.patientId").value(KOWALSKI))
                .andExpect(jsonPath("$.encounterId").value(KOWALSKI_HOSPITALIZATION))
                .andExpect(jsonPath("$.orderedById").value(DOCTOR_STAFF)) // z tokenu, nie z zadania
                .andExpect(jsonPath("$.createdById").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.updatedById").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.orderedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.status").value("ordered")) // status z zadania ignorowany
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.urgency").value("urgent"))
                .andExpect(jsonPath("$.fasting").value(false))
                .andExpect(jsonPath("$.plannedCollectionAt").value("2030-01-02T08:00:00Z"))
                .andExpect(jsonPath("$.diagnosisCode.system").value("SNOMED"))
                .andExpect(jsonPath("$.diagnosisCode.code").value("84114007"))
                .andExpect(jsonPath("$.diagnosisCode.display").value("Niewydolność serca"))
                .andExpect(jsonPath("$.clinicalInfo").value("Kontrola CRP."))
                .andExpect(jsonPath("$.notes").value("Pobrać rano"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].testCode").value("CRP"))
                .andExpect(jsonPath("$.items[0].testName").value("Nazwa w katalogu")) // snapshot z katalogu
                .andExpect(jsonPath("$.items[0].specimenType").value("blood"))
                .andExpect(jsonPath("$.items[1].testName").value("Morfologia krwi z rozmazem"))
                .andExpect(jsonPath("$.statusHistory", hasSize(1)))
                .andExpect(jsonPath("$.statusHistory[0].status").value("ordered"))
                .andExpect(jsonPath("$.statusHistory[0].byId").value(DOCTOR_STAFF))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(response, "$.id");

        assertThat(jdbc.queryForObject("select count(*) from lab_order_status_change where order_id = ?::uuid",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from lab_order_item where order_id = ?::uuid",
                Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForMap("select diagnosis_code_system s, diagnosis_code_value v from lab_order "
                + "where id = ?::uuid", id)).containsEntry("s", "SNOMED").containsEntry("v", "84114007");

        // zmiana katalogu po zleceniu nie zmienia zapisanego snapshotu
        jdbc.update("update lab_test set name = 'Jeszcze inna' where code = 'CRP'");
        as("nurse", get("/api/v1/lab-orders/{id}", id))
                .andExpect(jsonPath("$.items[0].testName").value("Nazwa w katalogu"));
        list("patientId=" + KOWALSKI + "&status=ordered").andExpect(jsonPath("$.items[0].id").value(id));
        assertThat(events.stream(LabOrderStatusChanged.class)).isEmpty(); // utworzenie to nie zmiana statusu
    }

    @Test
    void createWithoutOptionalFieldsOmitsThem() throws Exception {
        String response = create("doctor", KOWALSKI, orderJson("MORF", "blood"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.encounterId").doesNotExist())
                .andExpect(jsonPath("$.diagnosisCode").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.items[0].specimenId").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(response, "$.id");
        assertThat(jdbc.queryForMap("select diagnosis_code_system s, diagnosis_code_value v, diagnosis_code_display d "
                + "from lab_order where id = ?::uuid", id)).containsEntry("s", null).containsEntry("v", null)
                .containsEntry("d", null);
    }

    @Test
    void createWithFastingTestAndFastingTrueSucceeds() throws Exception {
        create("doctor", KOWALSKI, orderJson("GLU", "serum", true)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fasting").value(true));
        create("doctor", KOWALSKI, orderJson("MORF", "blood", true)).andExpect(status().isCreated());
    }

    @Test
    void createForUnknownPatientIs404() throws Exception {
        create("doctor", UUID.randomUUID().toString(), orderJson("MORF", "blood")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        create("doctor", "to-nie-uuid", orderJson("MORF", "blood")).andExpect(status().isNotFound());
    }

    @Test
    void createValidatesItems() throws Exception {
        create("doctor", KOWALSKI, orderBody("[]", "false")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("items"));
        create("doctor", KOWALSKI, orderBody(null, "false")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
        create("doctor", KOWALSKI, orderBody("[{\"testCode\":\"NIE_MA\",\"specimenType\":\"blood\"}]", "false"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].testCode"))
                .andExpect(jsonPath("$.errors[0].code").value("notFound"));
        create("doctor", KOWALSKI, orderBody("[{\"testCode\":\"MORF\",\"specimenType\":\"serum\"}]", "false"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].specimenType"))
                .andExpect(jsonPath("$.errors[0].code").value("notAllowed"));
        create("doctor", KOWALSKI, orderBody("[{\"testCode\":\"MORF\",\"specimenType\":\"blood\"},"
                + "{\"testCode\":\"MORF\",\"specimenType\":\"blood\"}]", "false"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[1].testCode"))
                .andExpect(jsonPath("$.errors[0].code").value("duplicate"));
        create("doctor", KOWALSKI, orderBody("[{\"testCode\":\"MORF\"}]", "false"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].specimenType"));
        create("doctor", KOWALSKI, orderBody("[{\"specimenType\":\"blood\"}]", "false"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].testCode"));
        create("doctor", KOWALSKI, orderBody("[{\"testCode\":\"MORF\",\"specimenType\":\"krew\"}]", "false"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].specimenType"));
        assertThat(jdbc.queryForObject("select count(*) from lab_order", Integer.class)).isEqualTo(7);
    }

    @Test
    void createCollectsAllItemErrorsAtOnce() throws Exception {
        create("doctor", KOWALSKI, orderBody("[{\"testCode\":\"NIE_MA\",\"specimenType\":\"blood\"},"
                + "{\"testCode\":\"MORF\",\"specimenType\":\"urine\"}]", "false"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", contains("items[0].testCode", "items[1].specimenType")));
    }

    @Test
    void createRequiresFastingForFastingTests() throws Exception {
        create("doctor", KOWALSKI, orderJson("GLU", "serum", false)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("fasting"))
                .andExpect(jsonPath("$.errors[0].code").value("fastingRequired"));
        create("doctor", KOWALSKI, orderBody("[{\"testCode\":\"MORF\",\"specimenType\":\"blood\"},"
                + "{\"testCode\":\"LIPID\",\"specimenType\":\"serum\"}]", "false"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("fasting"));
    }

    @Test
    void createValidatesRequiredFieldsAndReferences() throws Exception {
        String items = "[{\"testCode\":\"MORF\",\"specimenType\":\"blood\"}]";
        create("doctor", KOWALSKI, "{\"items\":" + items + ",\"fasting\":false,\"plannedCollectionAt\":"
                + "\"2030-01-01T08:00:00Z\",\"clinicalInfo\":\"x\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("urgency"));
        create("doctor", KOWALSKI, "{\"items\":" + items + ",\"urgency\":\"routine\",\"plannedCollectionAt\":"
                + "\"2030-01-01T08:00:00Z\",\"clinicalInfo\":\"x\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("fasting"));
        create("doctor", KOWALSKI, "{\"items\":" + items + ",\"urgency\":\"routine\",\"fasting\":false,"
                + "\"clinicalInfo\":\"x\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("plannedCollectionAt"));
        create("doctor", KOWALSKI, "{\"items\":" + items + ",\"urgency\":\"routine\",\"fasting\":false,"
                + "\"plannedCollectionAt\":\"2030-01-01T08:00:00Z\",\"clinicalInfo\":\"  \"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("clinicalInfo"));
        create("doctor", KOWALSKI, orderBody(items, "false").replace("\"routine\"", "\"asap\""))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("urgency"));
        create("doctor", KOWALSKI, orderBody(items, "false", ",\"diagnosisCode\":{\"system\":\"SNOMED\",\"code\":\"84114007\"}")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("diagnosisCode.display"));
        create("doctor", KOWALSKI, orderBody(items, "false", ",\"patientId\":\"" + SZYMANSKI + "\""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("patientId"));
        create("doctor", KOWALSKI, orderBody(items, "false", ",\"encounterId\":\"" + WISNIEWSKA_ENCOUNTER + "\"")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("encounterId"));
        create("doctor", KOWALSKI, "nie json").andExpect(status().isUnprocessableContent());
        assertThat(jdbc.queryForObject("select count(*) from lab_order", Integer.class)).isEqualTo(7);
    }

    // --- statusy ---

    @Test
    void orderWalksThroughAllStatusesWithActorsAndEvents() throws Exception {
        String id = createOrderAs("doctor");
        postStatus("lab-tech", id, "scheduled", "Termin ustalony").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("scheduled"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.updatedById").value(LAB_STAFF));
        postStatus("nurse", id, "specimen_collected").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("specimen_collected"));
        postStatus("lab-tech", id, "in_progress").andExpect(status().isOk());
        postStatus("admin", id, "completed", "Wyniki gotowe").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.statusHistory[*].status", contains("ordered", "scheduled",
                        "specimen_collected", "in_progress", "completed")))
                .andExpect(jsonPath("$.statusHistory[0].byId").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.statusHistory[1].byId").value(LAB_STAFF))
                .andExpect(jsonPath("$.statusHistory[1].note").value("Termin ustalony"))
                .andExpect(jsonPath("$.statusHistory[2].byId").value(NURSE_STAFF))
                .andExpect(jsonPath("$.statusHistory[2].note").doesNotExist())
                .andExpect(jsonPath("$.statusHistory[4].note").value("Wyniki gotowe"));

        as("doctor", get("/api/v1/lab-orders/{id}", id)).andExpect(jsonPath("$.statusHistory", hasSize(5)))
                .andExpect(jsonPath("$.status").value("completed"));
        assertThat(jdbc.queryForObject("select count(*) from lab_order_status_change where order_id = ?::uuid",
                Integer.class, id)).isEqualTo(5);
        assertThat(events.stream(LabOrderStatusChanged.class)).hasSize(4)
                .extracting(LabOrderStatusChanged::status).containsExactly(OrderStatus.SCHEDULED,
                        OrderStatus.SPECIMEN_COLLECTED, OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED);
        LabOrderStatusChanged first = events.stream(LabOrderStatusChanged.class).findFirst().orElseThrow();
        assertThat(first.orderId().toString()).isEqualTo(id);
        assertThat(first.previousStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(first.actorId().toString()).isEqualTo(LAB_STAFF);
        assertThat(first.orderedById().toString()).isEqualTo(DOCTOR_STAFF);
        assertThat(first.note()).isEqualTo("Termin ustalony");
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @MethodSource("allTransitions")
    void statusTransitionMatrix(OrderStatus from, OrderStatus to, int expected) throws Exception {
        setStatus(ORD_ORDERED, from);
        postStatus("lab-tech", ORD_ORDERED, to.wire()).andExpect(status().is(expected));
        if (expected == 409) {
            as("lab-tech", get("/api/v1/lab-orders/{id}", ORD_ORDERED)).andExpect(jsonPath("$.status").value(from.wire()));
            assertThat(events.stream(LabOrderStatusChanged.class)).isEmpty();
        } else {
            assertThat(events.stream(LabOrderStatusChanged.class)).singleElement()
                    .satisfies(e -> assertThat(e.previousStatus()).isEqualTo(from));
        }
    }

    static Stream<Arguments> allTransitions() {
        return Stream.of(OrderStatus.values()).flatMap(from -> Stream.of(OrderStatus.values())
                .filter(to -> to != OrderStatus.CANCELLED) // anulowanie: osobna akcja (test ponizej)
                .map(to -> Arguments.of(from, to, ALLOWED.get(from).contains(to) ? 200 : 409)));
    }

    @Test
    void conflictResponseIsProblemWithCode() throws Exception {
        postStatus("lab-tech", ORD_COMPLETED_4, "in_progress").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void statusCancelledViaStatusEndpointIsRejected() throws Exception {
        postStatus("lab-tech", ORD_ORDERED, "cancelled").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        postStatus("admin", ORD_ORDERED, "cancelled").andExpect(status().isUnprocessableContent());
        as("doctor", get("/api/v1/lab-orders/{id}", ORD_ORDERED)).andExpect(jsonPath("$.status").value("ordered"));
    }

    @Test
    void nurseMayOnlyCollectSpecimen() throws Exception {
        for (String target : List.of("scheduled", "in_progress", "completed", "ordered", "cancelled")) {
            postStatus("nurse", ORD_ORDERED, target).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        as("doctor", get("/api/v1/lab-orders/{id}", ORD_ORDERED)).andExpect(jsonPath("$.status").value("ordered"));
        postStatus("nurse", ORD_ORDERED, "specimen_collected", "Pobrano na oddziale").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("specimen_collected"))
                .andExpect(jsonPath("$.statusHistory[1].byId").value(NURSE_STAFF));
        // dozwolone dla roli, ale niedozwolone przejscie -> 409 (nie 403)
        postStatus("nurse", ORD_COMPLETED_1, "specimen_collected").andExpect(status().isConflict());
        postStatus("nurse", ORD_IN_PROGRESS, "specimen_collected").andExpect(status().isConflict());
    }

    @Test
    void statusValidationAndNotFound() throws Exception {
        as("lab-tech", post("/api/v1/lab-orders/{id}/status", ORD_ORDERED).contentType(JSON).content("{}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("status"));
        as("lab-tech", post("/api/v1/lab-orders/{id}/status", ORD_ORDERED).contentType(JSON)
                .content("{\"status\":\"gotowe\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("status"));
        postStatus("lab-tech", UUID.randomUUID().toString(), "scheduled").andExpect(status().isNotFound());
        postStatus("lab-tech", "to-nie-uuid", "scheduled").andExpect(status().isNotFound());
    }

    @Test
    void statusRespectsVersion() throws Exception {
        as("lab-tech", post("/api/v1/lab-orders/{id}/status", ORD_ORDERED).contentType(JSON)
                .content("{\"status\":\"scheduled\",\"version\":5}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        as("doctor", get("/api/v1/lab-orders/{id}", ORD_ORDERED)).andExpect(jsonPath("$.status").value("ordered"));
        as("lab-tech", post("/api/v1/lab-orders/{id}/status", ORD_ORDERED).contentType(JSON)
                .content("{\"status\":\"scheduled\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        // ponowne uzycie starej wersji = konflikt
        as("lab-tech", post("/api/v1/lab-orders/{id}/status", ORD_ORDERED).contentType(JSON)
                .content("{\"status\":\"specimen_collected\",\"version\":0}")).andExpect(status().isConflict());
    }

    // --- anulowanie ---

    @Test
    void cancelStoresReasonActorAndPublishesEvent() throws Exception {
        cancel("doctor", ORD_ORDERED, "  Zlecono omyłkowo  ").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.statusHistory", hasSize(2)))
                .andExpect(jsonPath("$.statusHistory[1].status").value("cancelled"))
                .andExpect(jsonPath("$.statusHistory[1].note").value("Zlecono omyłkowo"))
                .andExpect(jsonPath("$.statusHistory[1].byId").value(DOCTOR_STAFF));
        as("lab-tech", get("/api/v1/lab-orders/{id}", ORD_ORDERED)).andExpect(jsonPath("$.status").value("cancelled"));
        assertThat(events.stream(LabOrderStatusChanged.class)).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(e.previousStatus()).isEqualTo(OrderStatus.ORDERED);
            assertThat(e.actorId().toString()).isEqualTo(DOCTOR_STAFF);
            assertThat(e.note()).isEqualTo("Zlecono omyłkowo");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"ordered", "scheduled", "specimen_collected", "in_progress"})
    void cancelWorksFromEveryNonTerminalStatus(String from) throws Exception {
        setStatus(ORD_ORDERED, OrderStatus.valueOf(from.toUpperCase()));
        cancel("doctor", ORD_ORDERED, "Powód").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"completed", "cancelled"})
    void cancelFromTerminalStatusIsConflict(String from) throws Exception {
        setStatus(ORD_ORDERED, OrderStatus.valueOf(from.toUpperCase()));
        cancel("doctor", ORD_ORDERED, "Powód").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(events.stream(LabOrderStatusChanged.class)).isEmpty();
    }

    @Test
    void cancelOfMockCompletedOrderIsConflictAndDoubleCancelToo() throws Exception {
        cancel("doctor", ORD_COMPLETED_1, "Powód").andExpect(status().isConflict());
        cancel("doctor", ORD_SCHEDULED, "Powód").andExpect(status().isOk());
        cancel("doctor", ORD_SCHEDULED, "Jeszcze raz").andExpect(status().isConflict());
        postStatus("lab-tech", ORD_SCHEDULED, "specimen_collected").andExpect(status().isConflict());
    }

    @Test
    void cancelRequiresReasonAndExistingOrder() throws Exception {
        as("doctor", post("/api/v1/lab-orders/{id}/cancel", ORD_ORDERED).contentType(JSON).content("{}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        cancel("doctor", ORD_ORDERED, "   ").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        cancel("doctor", UUID.randomUUID().toString(), "Powód").andExpect(status().isNotFound());
        cancel("doctor", "to-nie-uuid", "Powód").andExpect(status().isNotFound());
        as("doctor", get("/api/v1/lab-orders/{id}", ORD_ORDERED)).andExpect(jsonPath("$.status").value("ordered"));
    }

    @Test
    void cancelRespectsVersion() throws Exception {
        as("doctor", post("/api/v1/lab-orders/{id}/cancel", ORD_ORDERED).contentType(JSON)
                .content("{\"reason\":\"Powód\",\"version\":3}")).andExpect(status().isConflict());
        as("doctor", post("/api/v1/lab-orders/{id}/cancel", ORD_ORDERED).contentType(JSON)
                .content("{\"reason\":\"Powód\",\"version\":0}")).andExpect(status().isOk());
    }

    // --- pomocnicze ---

    private void setStatus(String orderId, OrderStatus status) {
        // przed pierwszym odczytem encji w tej transakcji (kazdy przypadek testu ma wlasna transakcje)
        jdbc.update("update lab_order set status = ? where id = ?::uuid", status.wire(), orderId);
    }

    private String createOrderAs(String login) throws Exception {
        String response = create(login, KOWALSKI, orderJson("MORF", "blood")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(response, "$.id");
    }

    private ResultActions list(String query) throws Exception {
        return as("doctor", get("/api/v1/lab-orders?" + query));
    }

    private ResultActions create(String login, String patientId, String body) throws Exception {
        return as(login, post("/api/v1/patients/{id}/lab-orders", patientId).contentType(JSON).content(body));
    }

    private ResultActions postStatus(String login, String orderId, String status) throws Exception {
        return postStatus(login, orderId, status, null);
    }

    private ResultActions postStatus(String login, String orderId, String status, String note) throws Exception {
        String body = "{\"status\":\"" + status + "\"" + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}";
        return as(login, post("/api/v1/lab-orders/{id}/status", orderId).contentType(JSON).content(body));
    }

    private ResultActions cancel(String login, String orderId, String reason) throws Exception {
        return as(login, post("/api/v1/lab-orders/{id}/cancel", orderId).contentType(JSON)
                .content("{\"reason\":\"" + reason + "\"}"));
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

    private static String orderJson(String testCode, String specimen) {
        return orderJson(testCode, specimen, false);
    }

    private static String orderJson(String testCode, String specimen, boolean fasting) {
        return orderBody("[{\"testCode\":\"" + testCode + "\",\"specimenType\":\"" + specimen + "\"}]",
                String.valueOf(fasting));
    }

    /** Poprawne zlecenie z podmienialna lista pozycji (null = bez pola `items`). */
    private static String orderBody(String items, String fasting) {
        return orderBody(items, fasting, "");
    }

    private static String orderBody(String items, String fasting, String extra) {
        return "{" + (items == null ? "" : "\"items\":" + items + ",") + "\"urgency\":\"routine\",\"fasting\":" + fasting
                + ",\"plannedCollectionAt\":\"2030-01-01T08:00:00Z\",\"clinicalInfo\":\"Kontrola.\"" + extra + "}";
    }
}
