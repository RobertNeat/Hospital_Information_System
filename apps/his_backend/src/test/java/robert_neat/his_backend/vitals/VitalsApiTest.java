package robert_neat.his_backend.vitals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.vitals.events.VitalAnomalyDetected;

/**
 * Kontrakt parametrow zyciowych (API.md, par. 7) na danych mock: 75 odczytow. Pacjenci przyjeci (ostatni odczyt):
 * Szymanski (hr 125 warning, SpO2 89 i rr 28 critical), Kwiatkowski (hr 105 i temp 38.3 warning), pozostali bez
 * anomalii. Tokeny z `POST /auth/login`; aktor pochodzi z tokenu.
 */
@RecordApplicationEvents
class VitalsApiTest extends ApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private static final String KOWALSKI = "c078186c-c437-5fa8-8a5b-a6bf8883f8bf"; // admitted, 19 odczytow (4 h = monitor)
    private static final String KAMINSKA = "a8500c41-1152-563d-b249-363417666099"; // admitted, 12 odczytow
    private static final String MAZUR = "55cc6e9e-6413-58bc-88b6-6342579d8413"; // admitted, 16 odczytow
    private static final String SZYMANSKI = "50c8f3fa-ea66-581a-9207-f9c4c7131d26"; // admitted, critical
    private static final String KWIATKOWSKI = "0f0db024-a3c0-56a5-916d-3acf34a052e5"; // admitted, warning
    private static final String WOJCIK = "7e25abc6-1c68-5922-8f9b-e5a8d6eeb5c9"; // outpatient, 3 stare odczyty
    private static final String ZIELINSKA = "fc5513b6-647f-51d1-8e7e-35d54d4dc21c"; // registered, brak odczytow

    private static final String DOCTOR_STAFF = "3bc5ba72-1a62-3681-ba58-fa6c83501852";
    private static final String NURSE_STAFF = "28222254-25c0-34db-938f-9228b7a20c52";

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
        mvc.perform(get("/api/v1/patients/{id}/vitals", KOWALSKI)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patients/{id}/vitals/latest", KOWALSKI)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/vitals/ward-overview")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/patients/{id}/vitals", KOWALSKI).contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "nurse", "admin"})
    void readRolesSeeVitalsAndWardOverview(String login) throws Exception {
        as(login, get("/api/v1/patients/{id}/vitals", KOWALSKI)).andExpect(status().isOk());
        as(login, get("/api/v1/patients/{id}/vitals/latest", KOWALSKI)).andExpect(status().isOk());
        as(login, get("/api/v1/vitals/ward-overview")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lab-tech", "radiologist", "pharmacist", "registrar"})
    void otherRolesCannotReadVitals(String login) throws Exception {
        as(login, get("/api/v1/patients/{id}/vitals", KOWALSKI)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as(login, get("/api/v1/patients/{id}/vitals/latest", KOWALSKI)).andExpect(status().isForbidden());
        as(login, get("/api/v1/vitals/ward-overview")).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "nurse"})
    void doctorAndNurseRecordVitals(String login) throws Exception {
        record(login, KOWALSKI, "{\"context\":\"ward_round\",\"heartRate\":80}").andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "lab-tech", "radiologist", "pharmacist", "registrar"})
    void otherRolesCannotRecordVitals(String login) throws Exception {
        record(login, KOWALSKI, "{\"context\":\"ward_round\",\"heartRate\":80}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(events.stream(VitalAnomalyDetected.class).count()).isZero();
    }

    // --- odczyt: zakresy, sortowanie, latest ---

    @Test
    void allRangeReturnsEveryReadingOldestFirst() throws Exception {
        String json = as("doctor", get("/api/v1/patients/{id}/vitals", KOWALSKI).param("range", "all"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(19)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> times = JsonPath.read(json, "$[*].recordedAt");
        assertThat(times).isSorted().doesNotHaveDuplicates();
        assertThat(JsonPath.<List<String>>read(json, "$[*].patientId")).containsOnly(KOWALSKI);
    }

    @Test
    void missingRangeMeansAll() throws Exception {
        as("nurse", get("/api/v1/patients/{id}/vitals", KAMINSKA)).andExpect(jsonPath("$", hasSize(12)));
    }

    @Test
    void rangesNarrowTheWindow() throws Exception {
        // odczyty co 5-12 h wstecz od migracji: w oknie 24 h mieszcza sie te mlodsze niz 24 h (ten starszy lezy tuz za granica)
        range(KOWALSKI, "24h", 4); // 4, 9, 14, 19 h
        range(KAMINSKA, "24h", 4); // 3, 9, 15, 21 h
        range(MAZUR, "24h", 4); // 4, 10, 16, 22 h
        range(MAZUR, "7d", 16); // najstarszy 160 h < 168 h
        range(KOWALSKI, "7d", 19); // najstarszy 164 h
        range(KOWALSKI, "30d", 19);
        range(WOJCIK, "24h", 0);
        range(WOJCIK, "7d", 0);
        range(WOJCIK, "30d", 0); // 720 h = dokladnie 30 d przed migracja, czyli tuz poza oknem
        range(WOJCIK, "all", 3);
    }

    @Test
    void rangeIsRelativeToNowSoFreshReadingsEnterEveryWindow() throws Exception {
        record("nurse", WOJCIK, "{\"context\":\"office_exam\",\"heartRate\":70}").andExpect(status().isCreated());
        range(WOJCIK, "24h", 1);
        range(WOJCIK, "7d", 1);
        range(WOJCIK, "30d", 1);
        range(WOJCIK, "all", 4);
    }

    @Test
    void unknownRangeIs422() throws Exception {
        as("doctor", get("/api/v1/patients/{id}/vitals", KOWALSKI).param("range", "1y"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("range"));
    }

    @Test
    void patientWithoutReadingsGetsEmptyListAndNoContentForLatest() throws Exception {
        as("doctor", get("/api/v1/patients/{id}/vitals", ZIELINSKA)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        as("doctor", get("/api/v1/patients/{id}/vitals/latest", ZIELINSKA)).andExpect(status().isNoContent());
    }

    @Test
    void latestReturnsNewestReadingIncludingMonitorSource() throws Exception {
        as("doctor", get("/api/v1/patients/{id}/vitals/latest", KOWALSKI)).andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(KOWALSKI)).andExpect(jsonPath("$.source").value("monitor"))
                .andExpect(jsonPath("$.deviceId").value("mon-ward-01"))
                .andExpect(jsonPath("$.context").value("ward_round"));
        as("doctor", get("/api/v1/patients/{id}/vitals/latest", WOJCIK)).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("manual")).andExpect(jsonPath("$.context").value("office_exam"));
    }

    @Test
    void latestFollowsNewlyRecordedReading() throws Exception {
        String saved = record("nurse", KAMINSKA, "{\"context\":\"ward_round\",\"systolic\":118,\"diastolic\":76}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        as("doctor", get("/api/v1/patients/{id}/vitals/latest", KAMINSKA))
                .andExpect(jsonPath("$.id").value(JsonPath.<String>read(saved, "$.saved.id")))
                .andExpect(jsonPath("$.systolic").value(118));
    }

    @Test
    void unknownPatientIs404ForReadsAndWrite() throws Exception {
        UUID unknown = UUID.randomUUID();
        as("doctor", get("/api/v1/patients/{id}/vitals", unknown)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        as("doctor", get("/api/v1/patients/{id}/vitals/latest", unknown)).andExpect(status().isNotFound());
        as("doctor", get("/api/v1/patients/{id}/vitals", "to-nie-uuid")).andExpect(status().isNotFound());
        record("doctor", unknown.toString(), "{\"context\":\"ward_round\",\"heartRate\":80}")
                .andExpect(status().isNotFound());
    }

    // --- kontrakt JSON ---

    @Test
    void readingJsonUsesContractNamesAndPlainNumbers() throws Exception {
        String raw = as("doctor", get("/api/v1/patients/{id}/vitals/latest", KOWALSKI)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> row = JsonPath.read(raw, "$");
        assertThat(row.keySet()).contains("id", "patientId", "recordedAt", "recordedById", "context", "source",
                "systolic", "diastolic", "heartRate", "temperature", "spo2", "respiratoryRate", "painScore")
                .doesNotContain("notes", "encounterId", "recorded_at", "heart_rate");
        assertThat(raw).contains("\"temperature\":37,").doesNotContain("37.0").doesNotContain("E+");
        assertThat((String) row.get("recordedAt")).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
        assertThat(row.get("heartRate")).isEqualTo(78);
    }

    @Test
    void decimalTemperatureKeepsItsFraction() throws Exception {
        as("doctor", get("/api/v1/patients/{id}/vitals/latest", SZYMANSKI)).andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature").value(36.8)).andExpect(jsonPath("$.heartRate").value(125));
    }

    // --- zapis ---

    @Test
    void recordReturns201WithSavedReadingAndNoAnomaliesForNormalValues() throws Exception {
        String json = record("nurse", KOWALSKI, """
                {"context":"ward_round","systolic":120,"diastolic":80,"heartRate":72,"temperature":36.6,
                 "spo2":98,"respiratoryRate":16,"painScore":2,"notes":"  Bez uwag  "}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.anomalies", hasSize(0)))
                .andExpect(jsonPath("$.saved.patientId").value(KOWALSKI))
                .andExpect(jsonPath("$.saved.context").value("ward_round"))
                .andExpect(jsonPath("$.saved.source").value("manual"))
                .andExpect(jsonPath("$.saved.systolic").value(120))
                .andExpect(jsonPath("$.saved.temperature").value(36.6))
                .andExpect(jsonPath("$.saved.painScore").value(2))
                .andExpect(jsonPath("$.saved.notes").value("Bez uwag"))
                .andExpect(jsonPath("$.saved.recordedById").value(NURSE_STAFF))
                .andExpect(jsonPath("$.saved.recordedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Instant at = Instant.parse(JsonPath.read(json, "$.saved.recordedAt"));
        assertThat(at).isBetween(Instant.now().minusSeconds(30), Instant.now().plusSeconds(1));
        assertThat(events.stream(VitalAnomalyDetected.class).count()).isZero();
        String id = JsonPath.read(json, "$.saved.id");
        assertThat(jdbc.queryForObject("select count(*) from vital_signs where id = ?::uuid", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void actorComesFromTokenNotFromBody() throws Exception {
        record("doctor", KOWALSKI, "{\"context\":\"office_exam\",\"heartRate\":70,\"recordedById\":\"" + NURSE_STAFF
                + "\",\"id\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.saved.recordedById").value(DOCTOR_STAFF));
    }

    @Test
    void omittedMeasurementsAreAbsentInResponse() throws Exception {
        String json = record("nurse", KOWALSKI, "{\"context\":\"triage\",\"painScore\":4}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.anomalies", hasSize(0)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> saved = JsonPath.read(json, "$.saved");
        assertThat(saved.keySet()).doesNotContain("systolic", "diastolic", "heartRate", "temperature", "spo2",
                "respiratoryRate", "notes", "deviceId", "encounterId").contains("painScore");
    }

    @Test
    void monitorReadingKeepsDeviceAndExplicitRecordedAt() throws Exception {
        String at = Instant.now().minus(15, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString();
        record("nurse", KOWALSKI, "{\"context\":\"observation\",\"source\":\"monitor\",\"deviceId\":\"mon-icu-7\","
                + "\"recordedAt\":\"" + at + "\",\"spo2\":97}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.saved.source").value("monitor"))
                .andExpect(jsonPath("$.saved.deviceId").value("mon-icu-7"))
                .andExpect(jsonPath("$.saved.recordedAt").value(at));
    }

    @Test
    void encounterOfThePatientIsAccepted() throws Exception {
        String encounter = jdbc.queryForObject("select id::text from encounter where patient_id = ?::uuid limit 1",
                String.class, KOWALSKI);
        record("doctor", KOWALSKI, "{\"context\":\"ward_round\",\"heartRate\":70,\"encounterId\":\"" + encounter
                + "\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.saved.encounterId").value(encounter));
    }

    @Test
    void warningAnomalyIsReturnedAndPublishesEventWithWarningAnomalies() throws Exception {
        String json = record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"heartRate\":105,\"temperature\":38.2}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.anomalies", hasSize(2)))
                .andExpect(jsonPath("$.anomalies[*].type", contains("heartRate", "temperature")))
                .andExpect(jsonPath("$.anomalies[*].severity", contains("warning", "warning")))
                .andExpect(jsonPath("$.anomalies[*].direction", contains("high", "high")))
                .andExpect(jsonPath("$.anomalies[0].value").value(105))
                .andExpect(jsonPath("$.anomalies[1].value").value(38.2))
                .andExpect(jsonPath("$.anomalies[0].message").value("Tętno: wartość powyżej normy (105 /min)."))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((String) JsonPath.read(json, "$.anomalies[0].recordedAt"))
                .isEqualTo(JsonPath.read(json, "$.saved.recordedAt"));
        List<VitalAnomalyDetected> published = events.stream(VitalAnomalyDetected.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().anomalies()).allMatch(a -> a.severity() == AnomalySeverity.WARNING);
    }

    @Test
    void criticalAnomalyPublishesEventWithAllAnomalies() throws Exception {
        String json = record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"systolic\":75,\"heartRate\":105,"
                + "\"spo2\":85,\"respiratoryRate\":16}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.anomalies", hasSize(3)))
                .andExpect(jsonPath("$.anomalies[*].type", contains("systolic", "heartRate", "spo2")))
                .andExpect(jsonPath("$.anomalies[*].severity", contains("critical", "warning", "critical")))
                .andExpect(jsonPath("$.anomalies[*].direction", contains("low", "high", "low")))
                .andExpect(jsonPath("$.anomalies[2].message").value("Saturacja SpO₂: wartość krytycznie niska (85 %)."))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<VitalAnomalyDetected> published = events.stream(VitalAnomalyDetected.class).toList();
        assertThat(published).hasSize(1);
        VitalAnomalyDetected event = published.getFirst();
        assertThat(event.patientId()).hasToString(KOWALSKI);
        assertThat(event.vitalsId()).hasToString(JsonPath.<String>read(json, "$.saved.id"));
        assertThat(event.actorId()).hasToString(NURSE_STAFF);
        // zdarzenie niesie teraz WSZYSTKIE anomalie zapisu (takze warning), nie tylko krytyczne
        assertThat(event.anomalies()).extracting(a -> a.type().wire())
                .containsExactly("systolic", "heartRate", "spo2");
        assertThat(event.anomalies()).filteredOn(a -> a.severity() == AnomalySeverity.CRITICAL)
                .extracting(a -> a.type().wire()).containsExactly("systolic", "spo2");
    }

    /** Granice progow: wartosc rowna progowi jest jeszcze "w normie" / "jeszcze nie krytyczna" (porownania scisle). */
    @ParameterizedTest
    @CsvSource({
            "systolic,90,none", "systolic,89,warning/low", "systolic,80,warning/low", "systolic,79,critical/low",
            "systolic,140,none", "systolic,141,warning/high", "systolic,180,warning/high", "systolic,181,critical/high",
            "diastolic,60,none", "diastolic,59,warning/low", "diastolic,50,warning/low", "diastolic,49,critical/low",
            "diastolic,90,none", "diastolic,91,warning/high", "diastolic,110,warning/high", "diastolic,111,critical/high",
            "heartRate,50,none", "heartRate,49,warning/low", "heartRate,40,warning/low", "heartRate,39,critical/low",
            "heartRate,100,none", "heartRate,101,warning/high", "heartRate,130,warning/high", "heartRate,131,critical/high",
            "temperature,36.0,none", "temperature,35.9,warning/low", "temperature,35.0,warning/low",
            "temperature,34.9,critical/low", "temperature,37.5,none", "temperature,37.6,warning/high",
            "temperature,39.5,warning/high", "temperature,39.6,critical/high",
            "spo2,94,none", "spo2,93,warning/low", "spo2,90,warning/low", "spo2,89,critical/low", "spo2,100,none",
            "respiratoryRate,12,none", "respiratoryRate,11,warning/low", "respiratoryRate,8,warning/low",
            "respiratoryRate,7,critical/low", "respiratoryRate,20,none", "respiratoryRate,21,warning/high",
            "respiratoryRate,25,warning/high", "respiratoryRate,26,critical/high"})
    void anomalyRulesFollowThresholdTable(String type, String value, String expected) throws Exception {
        ResultActions result = record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"" + type + "\":" + value + "}")
                .andExpect(status().isCreated());
        if (expected.equals("none")) {
            result.andExpect(jsonPath("$.anomalies", hasSize(0)));
            return;
        }
        String[] parts = expected.split("/");
        result.andExpect(jsonPath("$.anomalies", hasSize(1))).andExpect(jsonPath("$.anomalies[0].type").value(type))
                .andExpect(jsonPath("$.anomalies[0].severity").value(parts[0]))
                .andExpect(jsonPath("$.anomalies[0].direction").value(parts[1]));
        // zdarzenie publikowane dla kazdej anomalii (warning i critical), nie tylko critical
        assertThat(events.stream(VitalAnomalyDetected.class).count()).isEqualTo(1);
    }

    @Test
    void anomaliesFollowTheThresholdTableNotConstants() throws Exception {
        jdbc.update("update vital_threshold set high = 70 where type = 'heartRate'");
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"heartRate\":75}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.anomalies[0].severity").value("warning"))
                .andExpect(jsonPath("$.anomalies[0].direction").value("high"));
    }

    // --- walidacja zapisu ---

    @Test
    void readingWithoutAnyMeasurementIs422() throws Exception {
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("measurements"))
                .andExpect(jsonPath("$.errors[0].code").value("required"));
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"notes\":\"tylko notatka\"}")
                .andExpect(status().isUnprocessableContent());
    }

    @ParameterizedTest
    @CsvSource({"systolic,39", "systolic,261", "diastolic,19", "diastolic,181", "heartRate,19", "heartRate,251",
            "temperature,29.9", "temperature,43.1", "spo2,49", "spo2,101", "respiratoryRate,3", "respiratoryRate,61"})
    void valueOutsideThresholdBoundsIs422(String type, String value) throws Exception {
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"" + type + "\":" + value + "}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value(type))
                .andExpect(jsonPath("$.errors[0].code").value("range"));
        assertThat(jdbc.queryForObject("select count(*) from vital_signs where patient_id = ?::uuid and "
                + "recorded_at > now() - interval '1 minute'", Integer.class, KOWALSKI)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"systolic,40", "systolic,260", "diastolic,20", "diastolic,180", "heartRate,20", "heartRate,250",
            "temperature,30", "temperature,43", "spo2,50", "spo2,100", "respiratoryRate,4", "respiratoryRate,60"})
    void valueOnThresholdBoundsIsAccepted(String type, String value) throws Exception {
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"" + type + "\":" + value + "}")
                .andExpect(status().isCreated());
    }

    @Test
    void allViolationsAreReportedTogether() throws Exception {
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"systolic\":999,\"spo2\":150,\"painScore\":11}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("systolic", "spo2", "painScore")));
    }

    @Test
    void fractionalWholeNumbersAndOverPreciseTemperatureAreRejected() throws Exception {
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"heartRate\":72.5}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("heartRate"));
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"temperature\":36.55}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("temperature"));
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"heartRate\":72.0,\"temperature\":36.50}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.saved.heartRate").value(72))
                .andExpect(jsonPath("$.saved.temperature").value(36.5));
    }

    @Test
    void painScoreRangeIs0To10() throws Exception {
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"painScore\":0}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.saved.painScore").value(0));
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"painScore\":10}").andExpect(status().isCreated());
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"painScore\":11}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("painScore"));
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"painScore\":-1}")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void recordedAtMustNotBeInTheFuture() throws Exception {
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"heartRate\":70,\"recordedAt\":\""
                + Instant.now().plus(1, ChronoUnit.HOURS) + "\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("recordedAt"));
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"heartRate\":70,\"recordedAt\":\""
                + Instant.now().minus(3, ChronoUnit.DAYS) + "\"}").andExpect(status().isCreated());
    }

    @Test
    void contextAndEnumsAreValidated() throws Exception {
        record("nurse", KOWALSKI, "{\"heartRate\":70}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("context"));
        record("nurse", KOWALSKI, "{\"context\":\"visit\",\"heartRate\":70}").andExpect(status().isUnprocessableContent());
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"source\":\"wearable\",\"heartRate\":70}")
                .andExpect(status().isUnprocessableContent());
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"heartRate\":\"szybko\"}")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void deviceIdRequiresMonitorSource() throws Exception {
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"heartRate\":70,\"deviceId\":\"mon-1\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("deviceId"));
    }

    @Test
    void bodyPatientMismatchAndForeignEncounterAreRejected() throws Exception {
        record("nurse", KOWALSKI, "{\"patientId\":\"" + SZYMANSKI + "\",\"context\":\"ward_round\",\"heartRate\":70}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("patientId"));
        record("nurse", KOWALSKI, "{\"patientId\":\"" + KOWALSKI + "\",\"context\":\"ward_round\",\"heartRate\":70}")
                .andExpect(status().isCreated());
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"heartRate\":70,\"encounterId\":\""
                + UUID.randomUUID() + "\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("encounterId"));
    }

    // --- przeglad oddzialu ---

    @Test
    void wardOverviewListsAdmittedPatientsMostSevereFirst() throws Exception {
        List<String> admitted = jdbc.queryForList("""
                select a.patient_id::text from admission a join patient p on p.id = a.patient_id
                where a.status = 'active' and p.status = 'admitted'""", String.class);
        String json = as("nurse", get("/api/v1/vitals/ward-overview")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(admitted.size())))
                .andExpect(jsonPath("$[0].patient.id").value(SZYMANSKI))
                .andExpect(jsonPath("$[1].patient.id").value(KWIATKOWSKI))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<List<String>>read(json, "$[*].patient.id")).containsExactlyInAnyOrderElementsOf(admitted);
        assertThat(JsonPath.<List<String>>read(json, "$[?(@.patient.id != '" + SZYMANSKI + "' && @.patient.id != '"
                + KWIATKOWSKI + "')].anomalies[*]")).isEmpty();
    }

    @Test
    void wardOverviewRowShapeMatchesContract() throws Exception {
        String json = as("doctor", get("/api/v1/vitals/ward-overview")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patient.status").value("admitted"))
                .andExpect(jsonPath("$[0].patient.wardName").exists())
                .andExpect(jsonPath("$[0].latest.patientId").value(SZYMANSKI))
                .andExpect(jsonPath("$[0].anomalies[*].type", contains("heartRate", "spo2", "respiratoryRate")))
                .andExpect(jsonPath("$[0].anomalies[*].severity", contains("warning", "critical", "critical")))
                .andExpect(jsonPath("$[0].anomalies[1].value").value(89))
                .andExpect(jsonPath("$[1].anomalies[*].type", contains("heartRate", "temperature")))
                .andExpect(jsonPath("$[1].anomalies[*].severity", contains("warning", "warning")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> row = JsonPath.read(json, "$[0]");
        assertThat(row.keySet()).containsExactlyInAnyOrder("patient", "latest", "anomalies", "lastMeasuredAgoMin");
        // ostatni odczyt Szymanskiego: 1 h przed migracja
        assertThat(((Number) row.get("lastMeasuredAgoMin")).longValue()).isBetween(59L, 120L);
    }

    @Test
    void wardOverviewFiltersByWardUsingActiveAdmissions() throws Exception {
        String ward = jdbc.queryForObject("select ward_id::text from admission where patient_id = ?::uuid and "
                + "status = 'active'", String.class, SZYMANSKI);
        List<String> expected = jdbc.queryForList("""
                select a.patient_id::text from admission a join patient p on p.id = a.patient_id
                where a.status = 'active' and p.status = 'admitted' and a.ward_id = ?::uuid""", String.class, ward);
        assertThat(expected).contains(SZYMANSKI);
        String json = as("doctor", get("/api/v1/vitals/ward-overview").param("wardId", ward))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].patient.id").value(SZYMANSKI))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<List<String>>read(json, "$[*].patient.id")).containsExactlyInAnyOrderElementsOf(expected);
        String wardName = jdbc.queryForObject("select name from ward where id = ?::uuid", String.class, ward);
        assertThat(JsonPath.<List<String>>read(json, "$[*].patient.wardName")).containsOnly(wardName);

        String otherWard = jdbc.queryForObject("select id::text from ward where id <> ?::uuid and id not in "
                + "(select ward_id from admission where status = 'active') limit 1", String.class, UUID.fromString(ward));
        as("doctor", get("/api/v1/vitals/ward-overview").param("wardId", otherWard)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void wardOverviewSkipsDischargedPatientsEvenWithStaleActiveAdmission() throws Exception {
        jdbc.update("update patient set status = 'discharged' where id = ?::uuid", KWIATKOWSKI);
        String json = as("doctor", get("/api/v1/vitals/ward-overview")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<List<String>>read(json, "$[*].patient.id")).doesNotContain(KWIATKOWSKI);
        assertThat(JsonPath.<List<String>>read(json, "$[*].patient.id")).contains(SZYMANSKI);
    }

    @Test
    void wardOverviewIncludesAdmittedPatientWithoutReadingsAtTheEnd() throws Exception {
        jdbc.update("delete from vital_signs where patient_id = ?::uuid", MAZUR);
        String json = as("doctor", get("/api/v1/vitals/ward-overview")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> ids = JsonPath.read(json, "$[*].patient.id");
        int index = ids.indexOf(MAZUR);
        assertThat(index).isGreaterThanOrEqualTo(2);
        Map<String, Object> row = JsonPath.read(json, "$[" + index + "]");
        assertThat(row.keySet()).containsExactlyInAnyOrder("patient", "anomalies").doesNotContain("latest");
        assertThat((List<?>) row.get("anomalies")).isEmpty();
    }

    @Test
    void wardOverviewReactsToNewCriticalReading() throws Exception {
        record("nurse", KOWALSKI, "{\"context\":\"ward_round\",\"spo2\":80}").andExpect(status().isCreated());
        as("doctor", get("/api/v1/vitals/ward-overview")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patient.id").value(SZYMANSKI)) // 3 anomalie, w tym 2 critical
                .andExpect(jsonPath("$[1].patient.id").value(KOWALSKI)) // 1 critical przed ostrzezeniami Kwiatkowskiego
                .andExpect(jsonPath("$[1].anomalies", hasSize(1)))
                .andExpect(jsonPath("$[2].patient.id").value(KWIATKOWSKI));
    }

    @Test
    void unknownWardIs404AndMalformedWardIs422() throws Exception {
        as("doctor", get("/api/v1/vitals/ward-overview").param("wardId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        as("doctor", get("/api/v1/vitals/ward-overview").param("wardId", "oddzial-1"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("wardId"));
    }

    // --- pomocnicze ---

    private void range(String patientId, String range, int expected) throws Exception {
        as("doctor", get("/api/v1/patients/{id}/vitals", patientId).param("range", range))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(expected)));
    }

    private ResultActions record(String login, String patientId, String body) throws Exception {
        return as(login, post("/api/v1/patients/{id}/vitals", patientId).contentType(JSON).content(body));
    }

    private ResultActions as(String login, MockHttpServletRequestBuilder request) throws Exception {
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
