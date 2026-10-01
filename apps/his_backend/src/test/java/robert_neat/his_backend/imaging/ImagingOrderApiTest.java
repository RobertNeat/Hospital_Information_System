package robert_neat.his_backend.imaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import robert_neat.his_backend.imaging.events.ImagingOrderStatusChanged;

/**
 * Kontrakt zlecen obrazowych (API.md, par. 5) na danych mock: 6 zlecen (4 completed, 1 in_progress, 1 ordered;
 * 3 stat, 1 urgent, 2 routine). Tokeny z `POST /auth/login`; aktor pochodzi z tokenu.
 */
@RecordApplicationEvents
class ImagingOrderApiTest extends ApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    // zlecenia mock
    private static final String ORD_CT = "92eb9648-eeb5-59b3-8307-b3c592976d73"; // completed, stat, TK-GL
    private static final String ORD_MRI = "e2da861b-a85e-5717-9eda-95f4cf78924e"; // completed, urgent, RM-GL-K
    private static final String ORD_ANGIO = "3a29c7db-b1fd-5cf1-88d8-78a9ef00d3ba"; // completed, stat, KORONARO
    private static final String ORD_USG_DONE = "c8e8a14e-b4d1-521f-b7e3-5e5aed1fab16"; // completed, routine
    private static final String ORD_RTG = "ec2e14bd-4918-5bae-abb5-5fac44f71be1"; // ordered, routine, right
    private static final String ORD_USG_IP = "3deb7e2e-6305-5631-8992-75248b8d0d3a"; // in_progress, stat

    private static final String KOWALSKI = "c078186c-c437-5fa8-8a5b-a6bf8883f8bf";
    private static final String KOWALSKI_HOSPITALIZATION = "11b7050d-6f4c-546a-9bf7-da0f024b363e";
    private static final String WISNIEWSKA_ENCOUNTER = "2e790042-5ce2-543b-baee-416bf421230d";
    private static final String SZYMANSKI = "50c8f3fa-ea66-581a-9207-f9c4c7131d26";
    private static final String DIABETIC = "a8500c41-1152-563d-b249-363417666099";
    private static final String USG_IP_PATIENT = "0f0db024-a3c0-56a5-916d-3acf34a052e5";

    private static final String DOCTOR_STAFF = "3bc5ba72-1a62-3681-ba58-fa6c83501852";
    private static final String NURSE_STAFF = "28222254-25c0-34db-938f-9228b7a20c52";
    private static final String RADIOLOGIST_STAFF = "1f93f564-8243-3ee5-8e75-a99cc5dead7c";

    private static final String SAFETY_OK = "{\"pregnancy\":\"no\",\"pacemakerOrImplant\":false,\"metalFragments\":false,"
            + "\"contrastAllergy\":false,\"claustrophobia\":false,\"confirmed\":true}";

    /** Dozwolone przejscia (poza `cancelled`, ktore idzie akcja /cancel) - niezaleznie od implementacji. */
    private static final Map<OrderStatus, List<OrderStatus>> ALLOWED = Map.of(
            OrderStatus.ORDERED, List.of(OrderStatus.SCHEDULED, OrderStatus.IN_PROGRESS),
            OrderStatus.SCHEDULED, List.of(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED),
            OrderStatus.SPECIMEN_COLLECTED, List.of(),
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
        mvc.perform(get("/api/v1/imaging-orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/imaging-orders/{id}", ORD_RTG)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/patients/{id}/imaging-orders", KOWALSKI).contentType(JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/imaging-orders/{id}/status", ORD_RTG).contentType(JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/imaging-orders/{id}/cancel", ORD_RTG).contentType(JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "radiologist", "admin"})
    void readRolesSeeOrders(String login) throws Exception {
        as(login, get("/api/v1/imaging-orders")).andExpect(status().isOk());
        as(login, get("/api/v1/imaging-orders/{id}", ORD_RTG)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nurse", "lab-tech", "pharmacist", "registrar"})
    void otherRolesCannotReadOrders(String login) throws Exception {
        as(login, get("/api/v1/imaging-orders")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as(login, get("/api/v1/imaging-orders/{id}", ORD_RTG)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nurse", "lab-tech", "admin", "radiologist", "pharmacist", "registrar"})
    void onlyDoctorCreatesAndCancels(String login) throws Exception {
        create(login, KOWALSKI, orderJson("USG-JB")).andExpect(status().isForbidden());
        cancel(login, ORD_RTG, "Powód").andExpect(status().isForbidden());
        as("doctor", get("/api/v1/imaging-orders/{id}", ORD_RTG)).andExpect(jsonPath("$.status").value("ordered"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "nurse", "lab-tech", "pharmacist", "registrar"})
    void rolesWithoutStatusPermissionGet403(String login) throws Exception {
        postStatus(login, ORD_RTG, "scheduled").andExpect(status().isForbidden());
        as("doctor", get("/api/v1/imaging-orders/{id}", ORD_RTG)).andExpect(jsonPath("$.status").value("ordered"));
    }

    // --- worklista ---

    @Test
    void worklistReturnsAllOrdersNewestFirstWithPageEnvelope() throws Exception {
        as("doctor", get("/api/v1/imaging-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(6)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[*].id",
                        contains(ORD_USG_IP, ORD_CT, ORD_ANGIO, ORD_USG_DONE, ORD_MRI, ORD_RTG)));
    }

    @Test
    void worklistFilters() throws Exception {
        list("status=completed").andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ORD_CT, ORD_MRI, ORD_ANGIO, ORD_USG_DONE)));
        list("status=in_progress").andExpect(jsonPath("$.items[*].id", contains(ORD_USG_IP)));
        list("status=ordered").andExpect(jsonPath("$.items[*].id", contains(ORD_RTG)));
        list("status=scheduled").andExpect(jsonPath("$.totalElements").value(0));
        list("status=cancelled").andExpect(jsonPath("$.totalElements").value(0));
        list("urgency=stat").andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ORD_CT, ORD_ANGIO, ORD_USG_IP)));
        list("urgency=urgent").andExpect(jsonPath("$.items[*].id", contains(ORD_MRI)));
        list("urgency=routine").andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ORD_USG_DONE, ORD_RTG)));
        list("modality=USG").andExpect(jsonPath("$.items[*].id", contains(ORD_USG_IP, ORD_USG_DONE)));
        list("modality=CT").andExpect(jsonPath("$.items[*].id", contains(ORD_CT)));
        list("modality=ANGIOGRAPHY").andExpect(jsonPath("$.items[*].id", contains(ORD_ANGIO)));
        list("modality=MMG").andExpect(jsonPath("$.totalElements").value(0));
        list("patientId=" + SZYMANSKI).andExpect(jsonPath("$.items[*].id", contains(ORD_CT)));
        list("patientId=" + UUID.randomUUID()).andExpect(jsonPath("$.totalElements").value(0));
        list("status=completed&urgency=stat").andExpect(jsonPath("$.items[*].id", contains(ORD_CT, ORD_ANGIO)));
        list("status=completed&modality=USG").andExpect(jsonPath("$.items[*].id", contains(ORD_USG_DONE)));
        list("status=ordered&urgency=stat").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void worklistPaginationAndSorting() throws Exception {
        list("size=4&page=0").andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.totalElements").value(6)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].id").value(ORD_USG_IP));
        list("size=4&page=1").andExpect(jsonPath("$.items[*].id", contains(ORD_MRI, ORD_RTG)))
                .andExpect(jsonPath("$.page").value(1));
        list("sort=orderedAt,asc&size=2").andExpect(jsonPath("$.items[*].id", contains(ORD_RTG, ORD_MRI)));
        list("sort=modality,asc&size=1").andExpect(jsonPath("$.items[0].modality").value("ANGIOGRAPHY"));
        list("sort=status,asc").andExpect(status().isOk());
        list("sort=urgency,desc").andExpect(status().isOk());
        list("sort=scheduledAt,asc").andExpect(status().isOk());
        list("sort=createdAt,desc").andExpect(status().isOk());
    }

    @Test
    void worklistRejectsUnknownSortAndBadFilterValues() throws Exception {
        list("sort=clinicalIndication,asc").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        list("status=zakonczone").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("in_progress")));
        list("urgency=asap").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("urgency"));
        list("modality=XRAY").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("modality"));
        list("modality=usg").andExpect(status().isUnprocessableContent()); // wielkie litery na drucie
        list("patientId=nie-uuid").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("patientId"));
    }

    // --- szczegoly i kontrakt JSON ---

    @Test
    void detailContainsSnapshotsSafetyAndStatusHistory() throws Exception {
        as("radiologist", get("/api/v1/imaging-orders/{id}", ORD_MRI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORD_MRI))
                .andExpect(jsonPath("$.patientId").value(DIABETIC))
                .andExpect(jsonPath("$.examCode").value("RM-GL-K"))
                .andExpect(jsonPath("$.examName").value("RM głowy z kontrastem"))
                .andExpect(jsonPath("$.modality").value("MRI"))
                .andExpect(jsonPath("$.bodyRegion").value("Głowa"))
                .andExpect(jsonPath("$.laterality").value("na"))
                .andExpect(jsonPath("$.contrast").value(true))
                .andExpect(jsonPath("$.urgency").value("urgent"))
                .andExpect(jsonPath("$.clinicalIndication").value("Ocena rozległości udaru niedokrwiennego mózgu."))
                .andExpect(jsonPath("$.orderedById").value("0ffcc103-5e1d-5346-aeba-8f9907cce3df"))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.safety.pregnancy").value("na"))
                .andExpect(jsonPath("$.safety.pacemakerOrImplant").value(false))
                .andExpect(jsonPath("$.safety.contrastAllergy").value(false))
                .andExpect(jsonPath("$.safety.creatinine").value(0.9))
                .andExpect(jsonPath("$.safety.egfr").value(78))
                .andExpect(jsonPath("$.safety.confirmed").value(true))
                .andExpect(jsonPath("$.statusHistory[*].status", contains("ordered", "scheduled", "completed")))
                .andExpect(jsonPath("$.statusHistory[0].at", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.version").value(0));
        as("doctor", get("/api/v1/imaging-orders/{id}", ORD_USG_IP))
                .andExpect(jsonPath("$.safety.pregnancy").value("no"))
                .andExpect(jsonPath("$.status").value("in_progress"));
        as("doctor", get("/api/v1/imaging-orders/{id}", ORD_RTG))
                .andExpect(jsonPath("$.laterality").value("right"))
                .andExpect(jsonPath("$.modality").value("RTG"));
    }

    @Test
    void jsonOmitsAbsentOptionalFieldsAndKeepsRequiredOnes() throws Exception {
        String json = as("doctor", get("/api/v1/imaging-orders/{id}", ORD_RTG)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> order = JsonPath.read(json, "$");
        assertThat(order.keySet()).contains("id", "patientId", "examCode", "examName", "modality", "bodyRegion",
                "laterality", "contrast", "clinicalIndication", "urgency", "safety", "orderedById", "orderedAt",
                "status", "statusHistory", "version", "createdAt", "updatedAt")
                .doesNotContain("encounterId", "clinicalQuestion", "diagnosisCode", "slotId", "scheduledAt",
                        "createdById", "updatedById");
        Map<String, Object> safety = JsonPath.read(json, "$.safety");
        assertThat(safety.keySet()).containsExactlyInAnyOrder("pregnancy", "pacemakerOrImplant", "metalFragments",
                "contrastAllergy", "claustrophobia", "confirmed"); // creatinine i egfr pomijane, gdy brak
        Map<String, Object> change = JsonPath.read(json, "$.statusHistory[0]");
        assertThat(change.keySet()).containsExactlyInAnyOrder("status", "at");
    }

    @Test
    void detailReturnsSnapshotNotCurrentCatalog() throws Exception {
        jdbc.update("update imaging_exam set name = 'Zmieniona nazwa', body_region = 'Inna' where code = 'RTG-KOL'");
        as("doctor", get("/api/v1/imaging-orders/{id}", ORD_RTG))
                .andExpect(jsonPath("$.examName").value("RTG stawu kolanowego"))
                .andExpect(jsonPath("$.bodyRegion").value("Staw kolanowy"));
    }

    @Test
    void detailOfUnknownOrderIs404() throws Exception {
        as("doctor", get("/api/v1/imaging-orders/{id}", UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        as("doctor", get("/api/v1/imaging-orders/{id}", "to-nie-uuid")).andExpect(status().isNotFound());
    }

    // --- utworzenie ---

    @Test
    void createWithSlotStoresSnapshotSchedulesAndReservesSlot() throws Exception {
        jdbc.update("update imaging_exam set name = 'Nazwa w katalogu' where code = 'RTG-KOL'");
        String slot = freeSlot("RTG", 0);
        String body = "{\"patientId\":\"" + KOWALSKI + "\",\"encounterId\":\"" + KOWALSKI_HOSPITALIZATION
                + "\",\"orderedById\":\"" + NURSE_STAFF + "\",\"examCode\":\"RTG-KOL\",\"examName\":\"Od klienta\","
                + "\"modality\":\"USG\",\"bodyRegion\":\"Inna\",\"laterality\":\"left\",\"contrast\":false,"
                + "\"clinicalIndication\":\"  Ból po urazie.  \",\"clinicalQuestion\":\" Złamanie? \","
                + "\"diagnosisCode\":{\"system\":\"ICD-10\",\"code\":\" S83.5 \",\"display\":\"Uraz więzadeł\"},"
                + "\"urgency\":\"urgent\",\"slotId\":\"" + slot + "\",\"scheduledAt\":\"2030-01-01T08:00:00Z\","
                + "\"status\":\"completed\",\"version\":9,"
                + "\"safety\":{\"pregnancy\":\"unknown\",\"pacemakerOrImplant\":true,\"metalFragments\":false,"
                + "\"contrastAllergy\":false,\"creatinine\":0.95,\"egfr\":88.5,\"claustrophobia\":true,"
                + "\"confirmed\":true}}";
        String response = create("doctor", KOWALSKI, body)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/v1/imaging-orders/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.patientId").value(KOWALSKI))
                .andExpect(jsonPath("$.encounterId").value(KOWALSKI_HOSPITALIZATION))
                .andExpect(jsonPath("$.orderedById").value(DOCTOR_STAFF)) // z tokenu, nie z zadania
                .andExpect(jsonPath("$.createdById").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.updatedById").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.orderedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.status").value("scheduled")) // slot -> od razu zaplanowane
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.examCode").value("RTG-KOL"))
                .andExpect(jsonPath("$.examName").value("Nazwa w katalogu")) // snapshot z katalogu
                .andExpect(jsonPath("$.modality").value("RTG"))
                .andExpect(jsonPath("$.bodyRegion").value("Staw kolanowy"))
                .andExpect(jsonPath("$.laterality").value("left"))
                .andExpect(jsonPath("$.contrast").value(false))
                .andExpect(jsonPath("$.clinicalIndication").value("Ból po urazie."))
                .andExpect(jsonPath("$.clinicalQuestion").value("Złamanie?"))
                .andExpect(jsonPath("$.diagnosisCode.system").value("ICD-10"))
                .andExpect(jsonPath("$.diagnosisCode.code").value("S83.5"))
                .andExpect(jsonPath("$.diagnosisCode.display").value("Uraz więzadeł"))
                .andExpect(jsonPath("$.urgency").value("urgent"))
                .andExpect(jsonPath("$.slotId").value(slot))
                .andExpect(jsonPath("$.safety.pregnancy").value("unknown"))
                .andExpect(jsonPath("$.safety.pacemakerOrImplant").value(true))
                .andExpect(jsonPath("$.safety.creatinine").value(0.95))
                .andExpect(jsonPath("$.safety.egfr").value(88.5))
                .andExpect(jsonPath("$.safety.claustrophobia").value(true))
                .andExpect(jsonPath("$.safety.confirmed").value(true))
                .andExpect(jsonPath("$.statusHistory[*].status", contains("ordered", "scheduled")))
                .andExpect(jsonPath("$.statusHistory[0].byId").value(DOCTOR_STAFF))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(response, "$.id");

        // scheduledAt = poczatek slotu (nie wartosc z zadania), slot zarezerwowany
        assertThat(jdbc.queryForObject("select scheduled_at = (select start_at from schedule_slot where id = ?::uuid) "
                + "from imaging_order where id = ?::uuid", Boolean.class, slot, id)).isTrue();
        assertThat(jdbc.queryForObject("select available from schedule_slot where id = ?::uuid", Boolean.class, slot))
                .isFalse();
        assertThat(jdbc.queryForObject("select count(*) from imaging_order_status_change where order_id = ?::uuid",
                Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForMap("select diagnosis_code_value v, safety_pregnancy p, safety_egfr e "
                + "from imaging_order where id = ?::uuid", id)).containsEntry("v", "S83.5")
                .containsEntry("p", "unknown");
        as("doctor", get("/api/v1/imaging-orders/{id}", id)).andExpect(jsonPath("$.scheduledAt").exists());
        list("patientId=" + KOWALSKI + "&status=scheduled").andExpect(jsonPath("$.items[0].id").value(id));
        assertThat(events.stream(ImagingOrderStatusChanged.class)).isEmpty(); // utworzenie to nie zmiana statusu

        // zmiana katalogu po zleceniu nie zmienia zapisanego snapshotu
        jdbc.update("update imaging_exam set name = 'Jeszcze inna' where code = 'RTG-KOL'");
        as("radiologist", get("/api/v1/imaging-orders/{id}", id))
                .andExpect(jsonPath("$.examName").value("Nazwa w katalogu"));
    }

    @Test
    void createWithoutSlotIsOrderedAndOmitsOptionalFields() throws Exception {
        String response = create("doctor", KOWALSKI, orderJson("USG-JB"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ordered"))
                .andExpect(jsonPath("$.laterality").value("na")) // pominiete = na
                .andExpect(jsonPath("$.modality").value("USG"))
                .andExpect(jsonPath("$.statusHistory", hasSize(1)))
                .andExpect(jsonPath("$.statusHistory[0].status").value("ordered"))
                .andExpect(jsonPath("$.slotId").doesNotExist())
                .andExpect(jsonPath("$.scheduledAt").doesNotExist())
                .andExpect(jsonPath("$.encounterId").doesNotExist())
                .andExpect(jsonPath("$.clinicalQuestion").doesNotExist())
                .andExpect(jsonPath("$.diagnosisCode").doesNotExist())
                .andExpect(jsonPath("$.safety.creatinine").doesNotExist())
                .andExpect(jsonPath("$.safety.egfr").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(response, "$.id");
        assertThat(jdbc.queryForMap("select diagnosis_code_system s, slot_id sl, scheduled_at sa, safety_creatinine c "
                + "from imaging_order where id = ?::uuid", id)).containsEntry("s", null).containsEntry("sl", null)
                .containsEntry("sa", null).containsEntry("c", null);
    }

    @Test
    void createAcceptsContrastForExamThatAllowsIt() throws Exception {
        create("doctor", KOWALSKI, orderBody("TK-KL-K", "\"laterality\":\"na\",", true, null, SAFETY_OK))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.contrast").value(true));
        create("doctor", KOWALSKI, orderBody("RTG-KOL", "\"laterality\":\"bilateral\",", false, null, SAFETY_OK))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.laterality").value("bilateral"));
    }

    @Test
    void createForUnknownPatientIs404() throws Exception {
        create("doctor", UUID.randomUUID().toString(), orderJson("USG-JB")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        create("doctor", "to-nie-uuid", orderJson("USG-JB")).andExpect(status().isNotFound());
    }

    @Test
    void createOnAlreadyBookedSlotIs409AndKeepsFirstOrder() throws Exception {
        String slot = freeSlot("USG", 0);
        String first = create("doctor", KOWALSKI, orderBody("USG-JB", "", false, slot, SAFETY_OK))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String firstId = JsonPath.read(first, "$.id");

        create("doctor", SZYMANSKI, orderBody("USG-JB", "", false, slot, SAFETY_OK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.status").value(409));
        assertThat(jdbc.queryForObject("select count(*) from imaging_order where slot_id = ?::uuid", Integer.class,
                slot)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from imaging_order", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select id::text from imaging_order where slot_id = ?::uuid", String.class,
                slot)).isEqualTo(firstId);
    }

    @Test
    void createOnSlotMarkedUnavailableIs409() throws Exception {
        String taken = jdbc.queryForObject(
                "select id::text from schedule_slot where modality = 'USG' and not available limit 1", String.class);
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, taken, SAFETY_OK)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(jdbc.queryForObject("select count(*) from imaging_order", Integer.class)).isEqualTo(6);
    }

    @Test
    void failedValidationDoesNotReserveTheSlot() throws Exception {
        String slot = freeSlot("USG", 0);
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, slot, SAFETY_OK.replace("true", "false")))
                .andExpect(status().isUnprocessableContent());
        assertThat(jdbc.queryForObject("select available from schedule_slot where id = ?::uuid", Boolean.class, slot))
                .isTrue();
    }

    @Test
    void createValidatesSafetyChecklist() throws Exception {
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, null, SAFETY_OK.replace("true", "false")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("safety.confirmed"));
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, null, null)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("safety"));
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, null, SAFETY_OK.replace("\"pregnancy\":\"no\",", "")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("safety.pregnancy"));
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, null, SAFETY_OK.replace("\"no\"", "\"maybe\"")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("safety.pregnancy"));
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, null,
                SAFETY_OK.replace("\"claustrophobia\":false,", "")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("safety.claustrophobia"));
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, null,
                SAFETY_OK.replace("\"confirmed\":true", "\"confirmed\":true,\"creatinine\":-1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("safety.creatinine"));
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, null,
                SAFETY_OK.replace("\"confirmed\":true", "\"confirmed\":true,\"egfr\":78.25")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("safety.egfr"));
        assertThat(jdbc.queryForObject("select count(*) from imaging_order", Integer.class)).isEqualTo(6);
    }

    @Test
    void createValidatesExamLateralityAndContrast() throws Exception {
        create("doctor", KOWALSKI, orderBody("NIE-MA", "", false, null, SAFETY_OK))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("examCode"))
                .andExpect(jsonPath("$.errors[0].code").value("notFound"));
        // badanie z lateralnoscia: brak albo `na` = 422
        create("doctor", KOWALSKI, orderBody("RTG-KOL", "", false, null, SAFETY_OK))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("laterality"));
        create("doctor", KOWALSKI, orderBody("MMG", "\"laterality\":\"na\",", false, null, SAFETY_OK))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("laterality"));
        create("doctor", KOWALSKI, orderBody("RTG-KOL", "\"laterality\":\"prawa\",", false, null, SAFETY_OK))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("laterality"));
        // kontrast przy badaniu bez `contrastPossible`
        create("doctor", KOWALSKI, orderBody("USG-JB", "", true, null, SAFETY_OK))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("contrast"))
                .andExpect(jsonPath("$.errors[0].code").value("notAllowed"));
        assertThat(jdbc.queryForObject("select count(*) from imaging_order", Integer.class)).isEqualTo(6);
    }

    @Test
    void createValidatesSlot() throws Exception {
        // modalnosc slotu niezgodna z badaniem
        String ctSlot = freeSlot("CT", 0);
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, ctSlot, SAFETY_OK))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("slotId"))
                .andExpect(jsonPath("$.errors[0].code").value("modalityMismatch"));
        assertThat(jdbc.queryForObject("select available from schedule_slot where id = ?::uuid", Boolean.class, ctSlot))
                .isTrue();
        // nieistniejacy slot
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, UUID.randomUUID().toString(), SAFETY_OK))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("slotId"))
                .andExpect(jsonPath("$.errors[0].code").value("notFound"));
        // slot nie jest UUID
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, "nie-uuid", SAFETY_OK))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("slotId"));
    }

    @Test
    void createValidatesRequiredFieldsAndReferences() throws Exception {
        create("doctor", KOWALSKI, "{\"contrast\":false,\"clinicalIndication\":\"x\",\"urgency\":\"routine\","
                + "\"safety\":" + SAFETY_OK + "}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("examCode"));
        create("doctor", KOWALSKI, "{\"examCode\":\"USG-JB\",\"clinicalIndication\":\"x\",\"urgency\":\"routine\","
                + "\"safety\":" + SAFETY_OK + "}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("contrast"));
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, null, SAFETY_OK).replace("Kontrola.", "  "))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("clinicalIndication"));
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, null, SAFETY_OK).replace("\"routine\"", "\"asap\""))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("urgency"));
        create("doctor", KOWALSKI, orderBody("USG-JB", "", false, null, SAFETY_OK)
                .replace("\"urgency\"", "\"urgencyX\"")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("urgency"));
        create("doctor", KOWALSKI, orderBody("USG-JB", "\"diagnosisCode\":{\"system\":\"ICD-10\",\"code\":\"I50.0\"},",
                false, null, SAFETY_OK)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("diagnosisCode.display"));
        create("doctor", KOWALSKI, orderBody("USG-JB", "\"patientId\":\"" + SZYMANSKI + "\",", false, null, SAFETY_OK))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("patientId"));
        create("doctor", KOWALSKI, orderBody("USG-JB", "\"encounterId\":\"" + WISNIEWSKA_ENCOUNTER + "\",", false,
                null, SAFETY_OK)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("encounterId"));
        create("doctor", KOWALSKI, "nie json").andExpect(status().isUnprocessableContent());
        assertThat(jdbc.queryForObject("select count(*) from imaging_order", Integer.class)).isEqualTo(6);
    }

    @Test
    void createCollectsAllErrorsAtOnce() throws Exception {
        create("doctor", KOWALSKI, orderBody("RTG-KOL", "", true, null, SAFETY_OK.replace("true", "false")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("laterality", "contrast", "safety.confirmed")));
    }

    // --- statusy ---

    @Test
    void orderWalksThroughAllStatusesWithActorsAndEvents() throws Exception {
        String slot = freeSlot("USG", 0);
        String id = createOrderAs("doctor", slot);
        postStatus("radiologist", id, "in_progress", "Pacjent w pracowni").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.updatedById").value(RADIOLOGIST_STAFF));
        postStatus("admin", id, "completed", "Badanie wykonane").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.statusHistory[*].status",
                        contains("ordered", "scheduled", "in_progress", "completed")))
                .andExpect(jsonPath("$.statusHistory[0].byId").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.statusHistory[2].byId").value(RADIOLOGIST_STAFF))
                .andExpect(jsonPath("$.statusHistory[2].note").value("Pacjent w pracowni"))
                .andExpect(jsonPath("$.statusHistory[3].note").value("Badanie wykonane"));

        as("doctor", get("/api/v1/imaging-orders/{id}", id)).andExpect(jsonPath("$.statusHistory", hasSize(4)))
                .andExpect(jsonPath("$.status").value("completed"));
        assertThat(jdbc.queryForObject("select count(*) from imaging_order_status_change where order_id = ?::uuid",
                Integer.class, id)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select available from schedule_slot where id = ?::uuid", Boolean.class, slot))
                .isFalse(); // wykonane badanie zostawia slot zajety
        assertThat(events.stream(ImagingOrderStatusChanged.class)).hasSize(2)
                .extracting(ImagingOrderStatusChanged::status)
                .containsExactly(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED);
        ImagingOrderStatusChanged first = events.stream(ImagingOrderStatusChanged.class).findFirst().orElseThrow();
        assertThat(first.orderId().toString()).isEqualTo(id);
        assertThat(first.patientId().toString()).isEqualTo(KOWALSKI);
        assertThat(first.previousStatus()).isEqualTo(OrderStatus.SCHEDULED);
        assertThat(first.actorId().toString()).isEqualTo(RADIOLOGIST_STAFF);
        assertThat(first.orderedById().toString()).isEqualTo(DOCTOR_STAFF);
        assertThat(first.note()).isEqualTo("Pacjent w pracowni");
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @MethodSource("allTransitions")
    void statusTransitionMatrix(OrderStatus from, OrderStatus to, int expected) throws Exception {
        setStatus(ORD_RTG, from);
        postStatus("radiologist", ORD_RTG, to.wire()).andExpect(status().is(expected));
        if (expected == 200) {
            assertThat(events.stream(ImagingOrderStatusChanged.class)).singleElement()
                    .satisfies(e -> assertThat(e.previousStatus()).isEqualTo(from));
        } else {
            assertThat(events.stream(ImagingOrderStatusChanged.class)).isEmpty();
            assertThat(jdbc.queryForObject("select status from imaging_order where id = ?::uuid", String.class,
                    ORD_RTG)).isEqualTo(from.wire());
        }
    }

    static Stream<Arguments> allTransitions() {
        return Stream.of(OrderStatus.values()).flatMap(from -> Stream.of(OrderStatus.values())
                .filter(to -> to != OrderStatus.CANCELLED) // anulowanie: osobna akcja (test ponizej)
                .map(to -> Arguments.of(from, to, expectedStatusCode(from, to))));
    }

    private static int expectedStatusCode(OrderStatus from, OrderStatus to) {
        if (to == OrderStatus.SPECIMEN_COLLECTED) {
            return 422; // nie dotyczy obrazowania, niezaleznie od stanu
        }
        return ALLOWED.get(from).contains(to) ? 200 : 409;
    }

    @Test
    void specimenCollectedIsUnprocessableForImaging() throws Exception {
        postStatus("radiologist", ORD_RTG, "specimen_collected").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("specimen_collected")));
        postStatus("admin", ORD_USG_IP, "specimen_collected").andExpect(status().isUnprocessableContent());
        as("doctor", get("/api/v1/imaging-orders/{id}", ORD_RTG)).andExpect(jsonPath("$.status").value("ordered"));
    }

    @Test
    void conflictResponseIsProblemWithCode() throws Exception {
        postStatus("radiologist", ORD_CT, "in_progress").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void statusCancelledViaStatusEndpointIsRejected() throws Exception {
        postStatus("radiologist", ORD_RTG, "cancelled").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        postStatus("admin", ORD_RTG, "cancelled").andExpect(status().isUnprocessableContent());
        as("doctor", get("/api/v1/imaging-orders/{id}", ORD_RTG)).andExpect(jsonPath("$.status").value("ordered"));
    }

    @Test
    void statusValidationAndNotFound() throws Exception {
        as("radiologist", post("/api/v1/imaging-orders/{id}/status", ORD_RTG).contentType(JSON).content("{}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("status"));
        as("radiologist", post("/api/v1/imaging-orders/{id}/status", ORD_RTG).contentType(JSON)
                .content("{\"status\":\"gotowe\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("status"));
        postStatus("radiologist", UUID.randomUUID().toString(), "scheduled").andExpect(status().isNotFound());
        postStatus("radiologist", "to-nie-uuid", "scheduled").andExpect(status().isNotFound());
    }

    @Test
    void statusRespectsVersion() throws Exception {
        as("radiologist", post("/api/v1/imaging-orders/{id}/status", ORD_RTG).contentType(JSON)
                .content("{\"status\":\"scheduled\",\"version\":5}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        as("doctor", get("/api/v1/imaging-orders/{id}", ORD_RTG)).andExpect(jsonPath("$.status").value("ordered"));
        as("radiologist", post("/api/v1/imaging-orders/{id}/status", ORD_RTG).contentType(JSON)
                .content("{\"status\":\"scheduled\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        as("radiologist", post("/api/v1/imaging-orders/{id}/status", ORD_RTG).contentType(JSON)
                .content("{\"status\":\"in_progress\",\"version\":0}")).andExpect(status().isConflict());
    }

    // --- anulowanie ---

    @Test
    void cancelStoresReasonActorAndPublishesEvent() throws Exception {
        cancel("doctor", ORD_RTG, "  Zlecono omyłkowo  ").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.statusHistory", hasSize(2)))
                .andExpect(jsonPath("$.statusHistory[1].status").value("cancelled"))
                .andExpect(jsonPath("$.statusHistory[1].note").value("Zlecono omyłkowo"))
                .andExpect(jsonPath("$.statusHistory[1].byId").value(DOCTOR_STAFF));
        as("radiologist", get("/api/v1/imaging-orders/{id}", ORD_RTG)).andExpect(jsonPath("$.status").value("cancelled"));
        assertThat(events.stream(ImagingOrderStatusChanged.class)).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(e.previousStatus()).isEqualTo(OrderStatus.ORDERED);
            assertThat(e.actorId().toString()).isEqualTo(DOCTOR_STAFF);
            assertThat(e.note()).isEqualTo("Zlecono omyłkowo");
        });
    }

    @Test
    void cancelReleasesSlotAndAllowsBookingItAgain() throws Exception {
        String slot = freeSlot("USG", 0);
        String id = createOrderAs("doctor", slot);
        assertThat(slotAvailable(slot)).isFalse();

        cancel("doctor", id, "Pacjent odwołał").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.slotId").value(slot)); // slot zostaje w historii zlecenia
        assertThat(slotAvailable(slot)).isTrue();

        // slot mozna zarezerwowac ponownie (czesciowy UQ pomija anulowane zlecenia)
        String second = createOrderAs("doctor", slot);
        assertThat(second).isNotEqualTo(id);
        assertThat(slotAvailable(slot)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from imaging_order where slot_id = ?::uuid", Integer.class,
                slot)).isEqualTo(2);
        // a ponowne anulowanie pierwszego (juz anulowanego) nie zwalnia slotu drugiego zlecenia
        cancel("doctor", id, "Jeszcze raz").andExpect(status().isConflict());
        assertThat(slotAvailable(slot)).isFalse();
    }

    @Test
    void cancelOfInProgressOrderWithSlotReleasesIt() throws Exception {
        String slot = freeSlot("USG", 0);
        String id = createOrderAs("doctor", slot);
        postStatus("radiologist", id, "in_progress").andExpect(status().isOk());
        cancel("doctor", id, "Przerwano").andExpect(status().isOk());
        assertThat(slotAvailable(slot)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ordered", "scheduled", "in_progress"})
    void cancelWorksFromEveryNonTerminalStatus(String from) throws Exception {
        setStatus(ORD_RTG, OrderStatus.valueOf(from.toUpperCase()));
        cancel("doctor", ORD_RTG, "Powód").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"completed", "cancelled"})
    void cancelFromTerminalStatusIsConflict(String from) throws Exception {
        setStatus(ORD_RTG, OrderStatus.valueOf(from.toUpperCase()));
        cancel("doctor", ORD_RTG, "Powód").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(events.stream(ImagingOrderStatusChanged.class)).isEmpty();
    }

    @Test
    void cancelOfMockCompletedOrderIsConflictAndDoubleCancelToo() throws Exception {
        cancel("doctor", ORD_CT, "Powód").andExpect(status().isConflict());
        cancel("doctor", ORD_USG_IP, "Powód").andExpect(status().isOk());
        cancel("doctor", ORD_USG_IP, "Jeszcze raz").andExpect(status().isConflict());
        postStatus("radiologist", ORD_USG_IP, "completed").andExpect(status().isConflict());
    }

    @Test
    void cancelRequiresReasonAndExistingOrder() throws Exception {
        as("doctor", post("/api/v1/imaging-orders/{id}/cancel", ORD_RTG).contentType(JSON).content("{}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        cancel("doctor", ORD_RTG, "   ").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        cancel("doctor", UUID.randomUUID().toString(), "Powód").andExpect(status().isNotFound());
        cancel("doctor", "to-nie-uuid", "Powód").andExpect(status().isNotFound());
        as("doctor", get("/api/v1/imaging-orders/{id}", ORD_RTG)).andExpect(jsonPath("$.status").value("ordered"));
    }

    @Test
    void cancelRespectsVersion() throws Exception {
        as("doctor", post("/api/v1/imaging-orders/{id}/cancel", ORD_RTG).contentType(JSON)
                .content("{\"reason\":\"Powód\",\"version\":3}")).andExpect(status().isConflict());
        as("doctor", post("/api/v1/imaging-orders/{id}/cancel", ORD_RTG).contentType(JSON)
                .content("{\"reason\":\"Powód\",\"version\":0}")).andExpect(status().isOk());
    }

    // --- pomocnicze ---

    private void setStatus(String orderId, OrderStatus status) {
        // przed pierwszym odczytem encji w tej transakcji (kazdy przypadek testu ma wlasna transakcje)
        jdbc.update("update imaging_order set status = ? where id = ?::uuid", status.wire(), orderId);
    }

    /** Wolny slot modalnosci (mock): `offset`-ty wg poczatku, sali i id. */
    private String freeSlot(String modality, int offset) {
        return jdbc.queryForObject("select id::text from schedule_slot where modality = ? and available "
                + "order by start_at, room, id limit 1 offset ?", String.class, modality, offset);
    }

    private boolean slotAvailable(String slotId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select available from schedule_slot where id = ?::uuid",
                Boolean.class, slotId));
    }

    private String createOrderAs(String login, String slot) throws Exception {
        String response = create(login, KOWALSKI, orderBody("USG-JB", "", false, slot, SAFETY_OK))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(response, "$.id");
    }

    private ResultActions list(String query) throws Exception {
        return as("doctor", get("/api/v1/imaging-orders?" + query));
    }

    private ResultActions create(String login, String patientId, String body) throws Exception {
        return as(login, post("/api/v1/patients/{id}/imaging-orders", patientId).contentType(JSON).content(body));
    }

    private ResultActions postStatus(String login, String orderId, String status) throws Exception {
        return postStatus(login, orderId, status, null);
    }

    private ResultActions postStatus(String login, String orderId, String status, String note) throws Exception {
        String body = "{\"status\":\"" + status + "\"" + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}";
        return as(login, post("/api/v1/imaging-orders/{id}/status", orderId).contentType(JSON).content(body));
    }

    private ResultActions cancel(String login, String orderId, String reason) throws Exception {
        return as(login, post("/api/v1/imaging-orders/{id}/cancel", orderId).contentType(JSON)
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

    /** Poprawne zlecenie badania bez kontrastu i slotu (`USG-JB` / `RTG-KOL` wymaga lateralnosci - patrz `orderBody`). */
    private static String orderJson(String examCode) {
        return orderBody(examCode, "", false, null, SAFETY_OK);
    }

    /**
     * Zlecenie z podmienialnymi polami: `extra` - dodatkowe pola JSON zakonczone przecinkiem (np. lateralnosc),
     * `slotId` i `safety` null = pominiete.
     */
    private static String orderBody(String examCode, String extra, boolean contrast, String slotId, String safety) {
        return "{" + extra + "\"examCode\":\"" + examCode + "\",\"contrast\":" + contrast
                + ",\"clinicalIndication\":\"Kontrola.\",\"urgency\":\"routine\""
                + (slotId == null ? "" : ",\"slotId\":\"" + slotId + "\"")
                + (safety == null ? "" : ",\"safety\":" + safety) + "}";
    }
}
