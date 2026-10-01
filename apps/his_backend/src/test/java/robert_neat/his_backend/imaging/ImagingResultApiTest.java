package robert_neat.his_backend.imaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.catalog.ImagingModality;
import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.security.StaffPrincipal;
import robert_neat.his_backend.imaging.events.ImagingOrderStatusChanged;
import robert_neat.his_backend.imaging.events.ImagingResultRecorded;

/**
 * Kontrakt wynikow obrazowych (API.md, par. 5) na danych mock: 5 wynikow (4 z zlecenia, 1 zewnetrzny; 2 krytyczne,
 * wszystkie `final`) oraz wewnetrzny zapis wynikow ({@link ImagingResultRecordingService}).
 */
@RecordApplicationEvents
class ImagingResultApiTest extends ApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private static final String SZYMANSKI = "50c8f3fa-ea66-581a-9207-f9c4c7131d26"; // CT, krytyczny
    private static final String DIABETIC = "a8500c41-1152-563d-b249-363417666099"; // MRI
    private static final String CARDIAC = "7466c824-06b6-57f2-8bae-2f20f77d64b6"; // angiografia, krytyczny
    private static final String PREOP = "17a3dd05-d7d5-5211-ba70-0fc6e51cc476"; // USG
    private static final String EXTERNAL_PATIENT = "2350ac70-4b44-51c1-8be3-5b736e27a2c9"; // USG bez zlecenia
    private static final String KOWALSKI = "c078186c-c437-5fa8-8a5b-a6bf8883f8bf"; // bez wynikow
    private static final String USG_IP_PATIENT = "0f0db024-a3c0-56a5-916d-3acf34a052e5";
    private static final String RTG_PATIENT = "7e25abc6-1c68-5922-8f9b-e5a8d6eeb5c9";

    private static final String RES_CT = "46b4363a-51a4-5eae-9151-05948f291573";
    private static final String RES_MRI = "1d2ddc8a-a36d-5ba4-a50c-3026e03f7cf9";
    private static final String RES_ANGIO = "202eded7-4eff-5810-8896-9dc66d5a7c88";
    private static final String RES_USG = "54b378dd-5c33-5a88-9463-192ce3f37e67";
    private static final String RES_EXTERNAL = "0c7552fd-1280-5d0d-b642-74547f544d67";

    private static final String ORD_CT = "92eb9648-eeb5-59b3-8307-b3c592976d73"; // completed
    private static final String ORD_RTG = "ec2e14bd-4918-5bae-abb5-5fac44f71be1"; // ordered, RTG
    private static final String ORD_USG_IP = "3deb7e2e-6305-5631-8992-75248b8d0d3a"; // in_progress, USG, stat
    private static final String USG_IP_ORDERED_BY = "fdf3ef3c-c118-5896-aa53-c2eff5aa7a21";

    private static final String DOCTOR_STAFF = "3bc5ba72-1a62-3681-ba58-fa6c83501852";
    private static final String RADIOLOGIST_STAFF = "1f93f564-8243-3ee5-8e75-a99cc5dead7c";

    private static final Map<String, String> TOKENS = new HashMap<>();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEvents events;
    @Autowired
    private ImagingResultRecordingService recording;

    // --- uwierzytelnienie i uprawnienia ---

    @Test
    void everyEndpointWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/patients/{id}/imaging-results", SZYMANSKI)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/imaging-results")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/imaging-results/{id}", RES_CT)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/imaging-results/{id}/acknowledge", RES_CT).contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "nurse", "radiologist", "admin"})
    void readRolesSeeResults(String login) throws Exception {
        as(login, get("/api/v1/patients/{id}/imaging-results", SZYMANSKI)).andExpect(status().isOk());
        as(login, get("/api/v1/imaging-results")).andExpect(status().isOk());
        as(login, get("/api/v1/imaging-results/{id}", RES_CT)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lab-tech", "pharmacist", "registrar"})
    void otherRolesCannotReadResults(String login) throws Exception {
        as(login, get("/api/v1/patients/{id}/imaging-results", SZYMANSKI)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as(login, get("/api/v1/imaging-results")).andExpect(status().isForbidden());
        as(login, get("/api/v1/imaging-results/{id}", RES_CT)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nurse", "lab-tech", "admin", "radiologist", "pharmacist", "registrar"})
    void onlyDoctorAcknowledges(String login) throws Exception {
        acknowledge(login, RES_CT).andExpect(status().isForbidden());
        assertThat(reviewedBy(RES_CT)).isNull();
    }

    // --- lista wynikow pacjenta ---

    @Test
    void patientListIsNewestFirstWithoutPaging() throws Exception {
        patientResults(SZYMANSKI, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(RES_CT));
        patientResults(KOWALSKI, null).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
        // wiele wynikow: dopisany wynik jest najnowszy
        jdbc.update("update imaging_result set patient_id = ?::uuid where id in (?::uuid, ?::uuid)", SZYMANSKI,
                RES_MRI, RES_ANGIO);
        patientResults(SZYMANSKI, null).andExpect(jsonPath("$[*].id", contains(RES_CT, RES_ANGIO, RES_MRI)));
    }

    @Test
    void patientListFilters() throws Exception {
        patientResults(CARDIAC, "all").andExpect(jsonPath("$", hasSize(1)));
        patientResults(CARDIAC, "critical").andExpect(jsonPath("$[*].id", contains(RES_ANGIO)));
        patientResults(CARDIAC, "abnormal").andExpect(jsonPath("$[*].id", contains(RES_ANGIO))); // critical = jedyna flaga
        patientResults(DIABETIC, "all").andExpect(jsonPath("$[*].id", contains(RES_MRI)));
        patientResults(DIABETIC, "critical").andExpect(jsonPath("$", empty()));
        patientResults(PREOP, "abnormal").andExpect(jsonPath("$", empty()));
    }

    @Test
    void patientListRejectsBadFilterAndUnknownPatient() throws Exception {
        patientResults(SZYMANSKI, "wszystkie").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("filter"));
        patientResults(UUID.randomUUID().toString(), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        patientResults("to-nie-uuid", null).andExpect(status().isNotFound());
    }

    // --- szczegoly i kontrakt JSON ---

    @Test
    void detailContainsAllContractFields() throws Exception {
        as("radiologist", get("/api/v1/imaging-results/{id}", RES_CT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RES_CT))
                .andExpect(jsonPath("$.patientId").value(SZYMANSKI))
                .andExpect(jsonPath("$.orderId").value(ORD_CT))
                .andExpect(jsonPath("$.modality").value("CT"))
                .andExpect(jsonPath("$.examName").value("TK głowy bez kontrastu"))
                .andExpect(jsonPath("$.bodyRegion").value("Głowa"))
                .andExpect(jsonPath("$.performedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.reportedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.radiologistName").value("lek. Grzegorz Nowicki"))
                .andExpect(jsonPath("$.technique").value(containsString("TK głowy")))
                .andExpect(jsonPath("$.findings").value(containsString("stłuczenia mózgu")))
                .andExpect(jsonPath("$.conclusion").value(containsString("Stłuczenie")))
                .andExpect(jsonPath("$.status").value("final"))
                .andExpect(jsonPath("$.imageCount").value(64))
                .andExpect(jsonPath("$.critical").value(true));
        as("doctor", get("/api/v1/imaging-results/{id}", RES_MRI)).andExpect(jsonPath("$.modality").value("MRI"))
                .andExpect(jsonPath("$.critical").value(false)).andExpect(jsonPath("$.imageCount").value(120));
        as("doctor", get("/api/v1/imaging-results/{id}", RES_ANGIO))
                .andExpect(jsonPath("$.modality").value("ANGIOGRAPHY"));
    }

    @Test
    void jsonOmitsAbsentOptionalFieldsAndKeepsRequiredOnes() throws Exception {
        String json = as("doctor", get("/api/v1/imaging-results/{id}", RES_EXTERNAL)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> result = JsonPath.read(json, "$");
        assertThat(result.keySet()).containsExactlyInAnyOrder("id", "patientId", "modality", "examName", "bodyRegion",
                "performedAt", "reportedAt", "radiologistName", "technique", "findings", "conclusion", "status",
                "imageCount", "critical"); // bez orderId, radiologistId, reviewedAt, reviewedById
    }

    @Test
    void detailOfUnknownResultIs404() throws Exception {
        as("doctor", get("/api/v1/imaging-results/{id}", UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        as("doctor", get("/api/v1/imaging-results/{id}", "to-nie-uuid")).andExpect(status().isNotFound());
    }

    @Test
    void detailReturnsSnapshotNotCurrentCatalog() throws Exception {
        jdbc.update("update imaging_exam set name = 'Zmieniona nazwa' where code = 'TK-GL'");
        as("doctor", get("/api/v1/imaging-results/{id}", RES_CT))
                .andExpect(jsonPath("$.examName").value("TK głowy bez kontrastu"));
    }

    // --- acknowledge ---

    @Test
    void acknowledgeStoresActorAndTimeFromToken() throws Exception {
        acknowledge("doctor", RES_CT).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RES_CT))
                .andExpect(jsonPath("$.reviewedById").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.reviewedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.critical").value(true));
        assertThat(reviewedBy(RES_CT)).isEqualTo(DOCTOR_STAFF);
        as("doctor", get("/api/v1/imaging-results/{id}", RES_CT)).andExpect(jsonPath("$.reviewedById").value(DOCTOR_STAFF));
    }

    @Test
    void acknowledgeIsIdempotentAndKeepsFirstAcknowledgement() throws Exception {
        String first = acknowledge("doctor", RES_CT).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        // ponowne potwierdzenie (rowniez przez innego lekarza) - 200 i bez zmian
        String second = acknowledge("doctor", RES_CT).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String third = acknowledge("user", RES_CT).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
        assertThat(reviewedBy(RES_CT)).isEqualTo(DOCTOR_STAFF);
    }

    @Test
    void acknowledgeAcceptsOptionalBodyAndIgnoresVersion() throws Exception {
        as("doctor", post("/api/v1/imaging-results/{id}/acknowledge", RES_MRI)).andExpect(status().isOk());
        as("doctor", post("/api/v1/imaging-results/{id}/acknowledge", RES_USG).contentType(JSON)
                .content("{\"version\":7}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedAt").exists());
    }

    @Test
    void acknowledgeOfUnknownResultIs404() throws Exception {
        acknowledge("doctor", UUID.randomUUID().toString()).andExpect(status().isNotFound());
        acknowledge("doctor", "to-nie-uuid").andExpect(status().isNotFound());
    }

    // --- inbox ---

    @Test
    void inboxReturnsAllResultsNewestFirstWithPatientSummary() throws Exception {
        as("doctor", get("/api/v1/imaging-results")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[*].id",
                        contains(RES_CT, RES_ANGIO, RES_EXTERNAL, RES_USG, RES_MRI)))
                .andExpect(jsonPath("$.items[0].patientId").value(SZYMANSKI))
                .andExpect(jsonPath("$.items[0].patient.id").value(SZYMANSKI))
                .andExpect(jsonPath("$.items[0].patient.mrn").exists())
                .andExpect(jsonPath("$.items[0].patient.firstName").exists())
                .andExpect(jsonPath("$.items[0].modality").value("CT"))
                .andExpect(jsonPath("$.items[0].findings").exists());
    }

    @Test
    void inboxFilters() throws Exception {
        inbox("filter=critical").andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[*].id", contains(RES_CT, RES_ANGIO)));
        inbox("filter=abnormal").andExpect(jsonPath("$.totalElements").value(2));
        inbox("filter=all").andExpect(jsonPath("$.totalElements").value(5));
        inbox("patientId=" + PREOP).andExpect(jsonPath("$.items[*].id", contains(RES_USG)));
        inbox("patientId=" + PREOP + "&filter=critical").andExpect(jsonPath("$.totalElements").value(0));
        inbox("patientId=" + UUID.randomUUID()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void inboxPaginationAndSorting() throws Exception {
        inbox("size=2&page=0").andExpect(jsonPath("$.items", hasSize(2))).andExpect(jsonPath("$.totalPages").value(3));
        inbox("size=2&page=2").andExpect(jsonPath("$.items[*].id", contains(RES_MRI))).andExpect(jsonPath("$.page").value(2));
        inbox("sort=performedAt,asc&size=1").andExpect(jsonPath("$.items[0].id").value(RES_MRI));
        inbox("sort=reportedAt,asc&size=1").andExpect(jsonPath("$.items[0].id").value(RES_MRI));
        inbox("sort=modality,asc&size=1").andExpect(jsonPath("$.items[0].modality").value("ANGIOGRAPHY"));
        inbox("sort=examName,asc").andExpect(status().isOk());
        inbox("sort=status,asc").andExpect(status().isOk());
        inbox("sort=critical,desc").andExpect(status().isOk());
    }

    @Test
    void inboxRejectsUnknownSortAndBadParameters() throws Exception {
        inbox("sort=findings,asc").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        inbox("filter=wszystkie").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("filter"));
        inbox("patientId=nie-uuid").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("patientId"));
    }

    @Test
    void inboxShowsAcknowledgement() throws Exception {
        acknowledge("doctor", RES_USG).andExpect(status().isOk());
        inbox("patientId=" + PREOP).andExpect(jsonPath("$.items[0].reviewedById").value(DOCTOR_STAFF));
    }

    // --- zapis wyniku: wynik zewnetrzny, snapshoty, zdarzenie ---

    @Test
    void externalResultIsStoredWithGivenSnapshotsAndPublishesEvent() throws Exception {
        ImagingResultResponse r = recording.recordResult(new RecordImagingResultCommand(UUID.fromString(KOWALSKI), null,
                ImagingModality.RTG, "RTG klatki piersiowej PA", "Klatka piersiowa", at(-3), at(-2),
                "lek. Zewnętrzny", null, "  Projekcja PA.  ", "  Bez zmian.  ", " Prawidłowy obraz. ",
                ImagingResultStatus.FINAL, null, false));
        assertThat(r.orderId()).isNull();
        assertThat(r.modality()).isEqualTo(ImagingModality.RTG);
        assertThat(r.radiologistName()).isEqualTo("lek. Zewnętrzny");
        assertThat(r.radiologistId()).isNull(); // brak sesji i brak radiologistId
        assertThat(r.technique()).isEqualTo("Projekcja PA.");
        assertThat(r.findings()).isEqualTo("Bez zmian.");
        assertThat(r.conclusion()).isEqualTo("Prawidłowy obraz.");
        assertThat(r.imageCount()).isZero();
        assertThat(r.critical()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from imaging_result", Integer.class)).isEqualTo(6);
        assertThat(events.stream(ImagingResultRecorded.class)).singleElement().satisfies(e -> {
            assertThat(e.resultId()).isEqualTo(r.id());
            assertThat(e.patientId()).isEqualTo(UUID.fromString(KOWALSKI));
            assertThat(e.orderId()).isNull();
            assertThat(e.orderedById()).isNull();
            assertThat(e.modality()).isEqualTo(ImagingModality.RTG);
            assertThat(e.critical()).isFalse();
            assertThat(e.actorId()).isNull();
        });
        assertThat(events.stream(ImagingOrderStatusChanged.class)).isEmpty();
        as("doctor", get("/api/v1/imaging-results/{id}", r.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(KOWALSKI)).andExpect(jsonPath("$.orderId").doesNotExist());
    }

    @Test
    void criticalFlagIsSetByRadiologistAndMarksEventCritical() {
        ImagingResultResponse r = recording.recordResult(external(KOWALSKI, ImagingResultStatus.FINAL, true));
        assertThat(r.critical()).isTrue();
        assertThat(events.stream(ImagingResultRecorded.class)).singleElement()
                .satisfies(e -> assertThat(e.critical()).isTrue());
        assertThat(jdbc.queryForObject("select critical from imaging_result where id = ?::uuid", Boolean.class,
                r.id().toString())).isTrue();
    }

    @Test
    void radiologistNameAndIdFallBackToActorAndEventCarriesActor() {
        String expected = jdbc.queryForObject(
                "select trim(title || ' ' || first_name || ' ' || last_name) from staff_member where id = ?::uuid",
                String.class, RADIOLOGIST_STAFF);
        UUID actor = UUID.fromString(RADIOLOGIST_STAFF);
        StaffPrincipal principal = () -> actor;
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        try {
            ImagingResultResponse r = recording.recordResult(new RecordImagingResultCommand(UUID.fromString(KOWALSKI),
                    null, ImagingModality.USG, "USG", "Jama brzuszna", at(-3), at(-2), " ", null, null, "Opis.",
                    "Wniosek.", ImagingResultStatus.FINAL, 5, false));
            assertThat(r.radiologistName()).isEqualTo(expected);
            assertThat(r.radiologistId()).isEqualTo(actor);
            assertThat(r.imageCount()).isEqualTo(5);
            assertThat(events.stream(ImagingResultRecorded.class)).singleElement()
                    .satisfies(e -> assertThat(e.actorId()).isEqualTo(actor));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void radiologistIdWithoutNameTakesNameFromStaff() {
        ImagingResultResponse r = recording.recordResult(new RecordImagingResultCommand(UUID.fromString(KOWALSKI), null,
                ImagingModality.USG, "USG", "Jama brzuszna", at(-3), at(-2), null,
                UUID.fromString(RADIOLOGIST_STAFF), null, "Opis.", "Wniosek.", ImagingResultStatus.FINAL, 1, false));
        assertThat(r.radiologistId()).isEqualTo(UUID.fromString(RADIOLOGIST_STAFF));
        assertThat(r.radiologistName()).contains("Radiolog");
    }

    // --- zapis wyniku: zlecenie i auto-completed ---

    @Test
    void finalResultCompletesOrderWithHistoryAndEvents() throws Exception {
        ImagingResultResponse r = recording.recordResult(orderResult(USG_IP_PATIENT, ORD_USG_IP,
                ImagingResultStatus.FINAL, true));
        assertThat(r.orderId()).isEqualTo(UUID.fromString(ORD_USG_IP));
        // snapshot ze zlecenia
        assertThat(r.modality()).isEqualTo(ImagingModality.USG);
        assertThat(r.examName()).isEqualTo("USG jamy brzusznej");
        assertThat(r.bodyRegion()).isEqualTo("Jama brzuszna");

        as("doctor", get("/api/v1/imaging-orders/{id}", ORD_USG_IP)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.statusHistory[*].status", contains("ordered", "in_progress", "completed")))
                .andExpect(jsonPath("$.statusHistory[2].note").value(containsString("automatycznie")))
                .andExpect(jsonPath("$.statusHistory[2].byId").doesNotExist()) // aktor systemowy
                .andExpect(jsonPath("$.version").value(1));
        assertThat(events.stream(ImagingOrderStatusChanged.class)).singleElement().satisfies(e -> {
            assertThat(e.orderId()).isEqualTo(UUID.fromString(ORD_USG_IP));
            assertThat(e.previousStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
            assertThat(e.status()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(e.orderedById()).isEqualTo(UUID.fromString(USG_IP_ORDERED_BY));
            assertThat(e.actorId()).isNull();
        });
        assertThat(events.stream(ImagingResultRecorded.class)).singleElement().satisfies(e -> {
            assertThat(e.orderedById()).isEqualTo(UUID.fromString(USG_IP_ORDERED_BY));
            assertThat(e.orderId()).isEqualTo(UUID.fromString(ORD_USG_IP));
            assertThat(e.critical()).isTrue();
        });
        // wynik widoczny w liscie pacjenta
        as("doctor", get("/api/v1/patients/{id}/imaging-results", USG_IP_PATIENT))
                .andExpect(jsonPath("$[0].orderId").value(ORD_USG_IP));
    }

    @Test
    void preliminaryResultDoesNotCompleteOrderUntilFinal() {
        recording.recordResult(orderResult(USG_IP_PATIENT, ORD_USG_IP, ImagingResultStatus.PRELIMINARY, false));
        assertThat(orderStatus(ORD_USG_IP)).isEqualTo("in_progress");
        assertThat(events.stream(ImagingOrderStatusChanged.class)).isEmpty();
        assertThat(events.stream(ImagingResultRecorded.class)).singleElement()
                .satisfies(e -> assertThat(e.status()).isEqualTo(ImagingResultStatus.PRELIMINARY));

        recording.recordResult(orderResult(USG_IP_PATIENT, ORD_USG_IP, ImagingResultStatus.FINAL, false));
        assertThat(orderStatus(ORD_USG_IP)).isEqualTo("completed");
        assertThat(events.stream(ImagingOrderStatusChanged.class)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from imaging_result where order_id = ?::uuid", Integer.class,
                ORD_USG_IP)).isEqualTo(2);
    }

    @Test
    void scheduledOrderAcceptsResultAndCompletes() {
        jdbc.update("update imaging_order set status = 'scheduled' where id = ?::uuid", ORD_USG_IP);
        recording.recordResult(orderResult(USG_IP_PATIENT, ORD_USG_IP, ImagingResultStatus.FINAL, false));
        assertThat(orderStatus(ORD_USG_IP)).isEqualTo("completed");
        assertThat(events.stream(ImagingOrderStatusChanged.class)).singleElement()
                .satisfies(e -> assertThat(e.previousStatus()).isEqualTo(OrderStatus.SCHEDULED));
    }

    @Test
    void resultIsRejectedForOrdersThatCannotAcceptIt() {
        // ordered (jeszcze niezaplanowane), completed (mock), cancelled -> 409
        assertThatThrownBy(() -> recording.recordResult(orderResult(RTG_PATIENT, ORD_RTG, ImagingResultStatus.FINAL,
                false))).isInstanceOf(ConflictException.class).hasMessageContaining("ordered");
        assertThatThrownBy(() -> recording.recordResult(orderResult(SZYMANSKI, ORD_CT, ImagingResultStatus.FINAL,
                false))).isInstanceOf(ConflictException.class).hasMessageContaining("completed");
        jdbc.update("update imaging_order set status = 'cancelled' where id = ?::uuid", ORD_USG_IP);
        assertThatThrownBy(() -> recording.recordResult(orderResult(USG_IP_PATIENT, ORD_USG_IP,
                ImagingResultStatus.FINAL, false))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("cancelled");
        assertThat(events.stream(ImagingResultRecorded.class)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from imaging_result", Integer.class)).isEqualTo(5);
    }

    @Test
    void secondResultAfterCompletionIsConflict() {
        recording.recordResult(orderResult(USG_IP_PATIENT, ORD_USG_IP, ImagingResultStatus.FINAL, false));
        assertThatThrownBy(() -> recording.recordResult(orderResult(USG_IP_PATIENT, ORD_USG_IP,
                ImagingResultStatus.PRELIMINARY, false))).isInstanceOf(ConflictException.class);
        assertThat(events.stream(ImagingResultRecorded.class)).hasSize(1);
    }

    @Test
    void externalResultNeverTouchesOrders() {
        recording.recordResult(external(USG_IP_PATIENT, ImagingResultStatus.FINAL, false));
        assertThat(orderStatus(ORD_USG_IP)).isEqualTo("in_progress");
        assertThat(events.stream(ImagingOrderStatusChanged.class)).isEmpty();
    }

    // --- zapis wyniku: walidacja 422 ---

    @Test
    void recordResultValidatesReferencesAndConsistency() {
        assertFields(external(UUID.randomUUID().toString(), ImagingResultStatus.FINAL, false), "patientId");
        // nieznane zlecenie, zlecenie innego pacjenta, niezgodna modalnosc
        assertFields(new RecordImagingResultCommand(UUID.fromString(USG_IP_PATIENT), UUID.randomUUID(), null, null,
                null, at(-3), at(-2), "Rad", null, null, "Opis", "Wniosek", ImagingResultStatus.FINAL, 1, false),
                "orderId");
        assertFields(orderResult(KOWALSKI, ORD_USG_IP, ImagingResultStatus.FINAL, false), "orderId");
        assertFields(new RecordImagingResultCommand(UUID.fromString(USG_IP_PATIENT), UUID.fromString(ORD_USG_IP),
                ImagingModality.CT, null, null, at(-3), at(-2), "Rad", null, null, "Opis", "Wniosek",
                ImagingResultStatus.FINAL, 1, false), "modality");
        // nieznany radiolog
        assertFields(new RecordImagingResultCommand(UUID.fromString(KOWALSKI), null, ImagingModality.USG, "USG",
                "Jama brzuszna", at(-3), at(-2), "Rad", UUID.randomUUID(), null, "Opis", "Wniosek",
                ImagingResultStatus.FINAL, 1, false), "radiologistId");
        assertThat(jdbc.queryForObject("select count(*) from imaging_result", Integer.class)).isEqualTo(5);
        assertThat(events.stream(ImagingResultRecorded.class)).isEmpty();
    }

    @Test
    void recordResultValidatesRequiredFieldsAndDates() {
        // wynik zewnetrzny bez snapshotu i bez pol wymaganych
        assertFields(new RecordImagingResultCommand(null, null, null, " ", null, null, null, "Rad", null, null, " ",
                null, null, null, false), "patientId", "modality", "examName", "bodyRegion", "performedAt",
                "reportedAt", "findings", "conclusion", "status");
        // opis przed wykonaniem badania, ujemna liczba obrazow
        assertFields(new RecordImagingResultCommand(UUID.fromString(KOWALSKI), null, ImagingModality.USG, "USG", "Jama",
                at(-1), at(-2), "Rad", null, null, "Opis", "Wniosek", ImagingResultStatus.FINAL, 1, false),
                "reportedAt");
        assertFields(new RecordImagingResultCommand(UUID.fromString(KOWALSKI), null, ImagingModality.USG, "USG", "Jama",
                at(-3), at(-2), "Rad", null, null, "Opis", "Wniosek", ImagingResultStatus.FINAL, -1, false),
                "imageCount");
        // zbyt dlugie pola tekstowe kolumn varchar
        assertFields(new RecordImagingResultCommand(UUID.fromString(KOWALSKI), null, ImagingModality.USG,
                "x".repeat(201), "Jama", at(-3), at(-2), "Rad", null, null, "Opis", "Wniosek",
                ImagingResultStatus.FINAL, 1, false), "examName");
        // brak radiologa: bez nazwy, bez id i bez sesji
        assertThatThrownBy(() -> recording.recordResult(new RecordImagingResultCommand(UUID.fromString(KOWALSKI), null,
                ImagingModality.USG, "USG", "Jama", at(-3), at(-2), null, null, null, "Opis", "Wniosek",
                ImagingResultStatus.FINAL, 1, false))).isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> assertThat(((ValidationFailedException) e).getErrors())
                        .extracting(f -> f.field()).containsExactly("radiologistName"));
        assertThat(events.stream(ImagingResultRecorded.class)).isEmpty();
    }

    // --- pomocnicze ---

    private void assertFields(RecordImagingResultCommand command, String... fields) {
        assertThatThrownBy(() -> recording.recordResult(command)).isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> assertThat(((ValidationFailedException) e).getErrors())
                        .extracting(f -> f.field()).containsExactlyInAnyOrder(fields));
    }

    private static Instant at(int hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS);
    }

    /** Wynik zewnetrzny (bez zlecenia), pelny snapshot z polecenia. */
    private static RecordImagingResultCommand external(String patientId, ImagingResultStatus status, boolean critical) {
        return new RecordImagingResultCommand(UUID.fromString(patientId), null, ImagingModality.USG,
                "USG jamy brzusznej", "Jama brzuszna", at(-3), at(-2), "lek. Zewnętrzny", null, null, "Opis badania.",
                "Wniosek.", status, 4, critical);
    }

    /** Wynik zlecenia: snapshot (modalnosc, nazwa, okolica) pochodzi ze zlecenia. */
    private static RecordImagingResultCommand orderResult(String patientId, String orderId,
            ImagingResultStatus status, boolean critical) {
        return new RecordImagingResultCommand(UUID.fromString(patientId), UUID.fromString(orderId), null, null, null,
                at(-3), at(-2), "lek. Test", null, null, "Opis badania.", "Wniosek.", status, 4, critical);
    }

    private String orderStatus(String orderId) {
        return jdbc.queryForObject("select status from imaging_order where id = ?::uuid", String.class, orderId);
    }

    private String reviewedBy(String resultId) {
        return jdbc.queryForObject("select reviewed_by_id::text from imaging_result where id = ?::uuid", String.class,
                resultId);
    }

    private ResultActions patientResults(String patientId, String filter) throws Exception {
        return as("doctor", get("/api/v1/patients/{id}/imaging-results" + (filter == null ? "" : "?filter=" + filter),
                patientId));
    }

    private ResultActions inbox(String query) throws Exception {
        return as("doctor", get("/api/v1/imaging-results?" + query));
    }

    private ResultActions acknowledge(String login, String resultId) throws Exception {
        return as(login, post("/api/v1/imaging-results/{id}/acknowledge", resultId).contentType(JSON).content("{}"));
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
