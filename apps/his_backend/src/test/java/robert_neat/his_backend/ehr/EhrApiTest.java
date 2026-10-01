package robert_neat.his_backend.ehr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.ehr.events.AllergyRecorded;
import robert_neat.his_backend.ehr.events.ClinicalNoteCreated;
import robert_neat.his_backend.ehr.events.DiagnosisRecorded;

/**
 * Kontrakt EHR (`/patients/{id}/...`, `/dictionaries/icd-10`) na danych mock: 13 notatek, 18 diagnoz, 7 alergii,
 * 4 przeciwwskazania, 10 leczen, 8 epizodow, 16 kontaktow. Uwierzytelnianie tokenami z `POST /auth/login`
 * (aktor zapisu pochodzi z tokenu).
 */
@RecordApplicationEvents
class EhrApiTest extends ApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private static final String KOWALSKI = "c078186c-c437-5fa8-8a5b-a6bf8883f8bf"; // 3 notatki, 3 diagnozy, 3 kontakty
    private static final String WISNIEWSKA = "7466c824-06b6-57f2-8bae-2f20f77d64b6";
    private static final String WOJCIK = "7e25abc6-1c68-5922-8f9b-e5a8d6eeb5c9"; // 1 diagnoza, brak epizodow
    private static final String SZYMANSKI = "50c8f3fa-ea66-581a-9207-f9c4c7131d26"; // brak alergii

    private static final String KOWALSKI_ADMISSION_NOTE = "725473be-5cdf-53a2-b7f0-d9638383648b";
    private static final String KOWALSKI_NURSING_NOTE = "76cdea00-f4be-5456-a363-01c35b8ace71";
    private static final String KOWALSKI_HOSPITALIZATION = "11b7050d-6f4c-546a-9bf7-da0f024b363e";
    private static final String WISNIEWSKA_ENCOUNTER = "2e790042-5ce2-543b-baee-416bf421230d";
    private static final String KOWALSKI_EPISODE = "f3b18e7b-f083-5fe4-b1fc-f2fb9155af71";
    private static final String KOWALSKI_HF_DIAGNOSIS = "04bc0ba0-f1c2-5a59-83cf-eef0b98b9078";

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

    @ParameterizedTest
    @ValueSource(strings = {"ehr-summary", "encounters", "episodes", "clinical-notes", "diagnoses", "allergies",
            "contraindications", "treatments"})
    void readsWithoutTokenAreUnauthorized(String resource) throws Exception {
        mvc.perform(get("/api/v1/patients/{id}/{r}", KOWALSKI, resource))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void writesAndDictionaryWithoutTokenAreUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/patients/{id}/clinical-notes", KOWALSKI).contentType(JSON).content(noteJson("progress")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/patients/{id}/diagnoses", KOWALSKI).contentType(JSON).content(diagnosisJson()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/patients/{id}/allergies", KOWALSKI).contentType(JSON).content(allergyJson()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/dictionaries/icd-10")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "nurse", "admin"})
    void fullReadRolesSeeEverything(String login) throws Exception {
        for (String resource : List.of("ehr-summary", "encounters", "episodes", "clinical-notes", "diagnoses",
                "allergies", "contraindications", "treatments")) {
            read(login, KOWALSKI, resource).andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"lab-tech", "radiologist", "pharmacist"})
    void limitedReadRolesSeeOnlyClinicalSafetyData(String login) throws Exception {
        for (String resource : List.of("diagnoses", "allergies", "contraindications", "treatments")) {
            read(login, KOWALSKI, resource).andExpect(status().isOk());
        }
        for (String resource : List.of("ehr-summary", "encounters", "episodes", "clinical-notes")) {
            read(login, KOWALSKI, resource).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void registrarHasNoEhrAccess() throws Exception {
        for (String resource : List.of("ehr-summary", "encounters", "episodes", "clinical-notes", "diagnoses",
                "allergies", "contraindications", "treatments")) {
            read("registrar", KOWALSKI, resource).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/patients/{id}/clinical-notes", KOWALSKI).header(HttpHeaders.AUTHORIZATION, bearer("registrar"))
                .contentType(JSON).content(noteJson("progress"))).andExpect(status().isForbidden());
    }

    @Test
    void unknownPatientIs404ForEveryRead() throws Exception {
        String unknown = UUID.randomUUID().toString();
        for (String resource : List.of("ehr-summary", "encounters", "episodes", "clinical-notes", "diagnoses",
                "allergies", "contraindications", "treatments")) {
            read("doctor", unknown, resource).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            read("doctor", "to-nie-uuid", resource).andExpect(status().isNotFound());
        }
    }

    // --- odczyty ---

    @Test
    void notesAreListedNewestFirstInContractShape() throws Exception {
        read("doctor", KOWALSKI, "clinical-notes")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JSON))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].id").value(KOWALSKI_ADMISSION_NOTE)) // najstarsza (76 h)
                .andExpect(jsonPath("$[2].patientId").value(KOWALSKI))
                .andExpect(jsonPath("$[2].encounterId").value(KOWALSKI_HOSPITALIZATION))
                .andExpect(jsonPath("$[2].authorId").value("16259545-f97c-531d-b9cd-6ba115379372"))
                .andExpect(jsonPath("$[2].category").value("admission"))
                .andExpect(jsonPath("$[2].title").value("Przyjęcie do oddziału"))
                .andExpect(jsonPath("$[2].symptoms", containsInAnyOrder("duszność wysiłkowa",
                        "obrzęki kończyn dolnych", "osłabienie")))
                .andExpect(jsonPath("$[2].createdAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$[2].version").value(0))
                // pola opcjonalne bez wartosci sa pomijane (NON_ABSENT), nie null
                .andExpect(jsonPath("$[2].createdById").doesNotExist())
                .andExpect(jsonPath("$[?(@.id=='" + KOWALSKI_NURSING_NOTE + "')].category").value("nursing"))
                .andExpect(jsonPath("$[?(@.id=='" + KOWALSKI_NURSING_NOTE + "')].symptoms").isEmpty());
        // notatka bez kontaktu nie ma pola encounterId
        read("doctor", WISNIEWSKA, "clinical-notes").andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void encountersAreListedNewestFirst() throws Exception {
        read("doctor", KOWALSKI, "encounters")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].type").value("consultation")) // 52 h temu
                .andExpect(jsonPath("$[0].status").value("finished"))
                .andExpect(jsonPath("$[0].endAt").exists())
                .andExpect(jsonPath("$[1].id").value(KOWALSKI_HOSPITALIZATION)) // 76 h temu
                .andExpect(jsonPath("$[1].patientId").value(KOWALSKI))
                .andExpect(jsonPath("$[1].type").value("hospitalization"))
                .andExpect(jsonPath("$[1].status").value("in_progress"))
                .andExpect(jsonPath("$[1].wardId").value("25c25490-5067-5aaa-bcf5-5dc23f56588b"))
                .andExpect(jsonPath("$[1].practitionerId").value("16259545-f97c-531d-b9cd-6ba115379372"))
                .andExpect(jsonPath("$[1].reason").exists())
                .andExpect(jsonPath("$[1].startAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$[1].endAt").doesNotExist())
                .andExpect(jsonPath("$[1].createdAt").doesNotExist())
                .andExpect(jsonPath("$[2].type").value("visit"))
                .andExpect(jsonPath("$[2].wardId").doesNotExist());
    }

    @Test
    void episodesCarryDiagnosisIds() throws Exception {
        read("nurse", KOWALSKI, "episodes")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(KOWALSKI_EPISODE))
                .andExpect(jsonPath("$[0].title").value("Zaostrzenie niewydolności serca"))
                .andExpect(jsonPath("$[0].status").value("active"))
                .andExpect(jsonPath("$[0].endAt").doesNotExist())
                .andExpect(jsonPath("$[0].diagnosisIds", contains(KOWALSKI_HF_DIAGNOSIS)));
        read("doctor", WISNIEWSKA, "episodes")
                .andExpect(jsonPath("$[0].diagnosisIds", hasSize(2)));
        read("doctor", "dfb16109-f62b-5791-a571-a3652caa0ea7", "episodes")
                .andExpect(jsonPath("$[0].status").value("closed"))
                .andExpect(jsonPath("$[0].endAt").exists());
        read("doctor", WOJCIK, "episodes").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void diagnosesUseCodingWithWireSystemValue() throws Exception {
        read("doctor", WISNIEWSKA, "diagnoses")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.code.code=='I21')].code.system").value("ICD-10"))
                .andExpect(jsonPath("$[?(@.code.code=='I21')].code.display").value("Ostry zawał serca"))
                .andExpect(jsonPath("$[?(@.code.code=='I21')].type").value("primary"))
                .andExpect(jsonPath("$[?(@.code.code=='I21')].status").value("active"))
                .andExpect(jsonPath("$[?(@.code.code=='I21')].encounterId").value(WISNIEWSKA_ENCOUNTER))
                .andExpect(jsonPath("$[?(@.code.code=='I21')].diagnosedById").value("6a2063f1-ade9-52c8-a1b0-f894c0093d46"))
                .andExpect(jsonPath("$[?(@.code.code=='I21')].version").value(0))
                .andExpect(jsonPath("$[?(@.code.code=='E78.0')].type").value("chronic"))
                .andExpect(jsonPath("$[?(@.code.code=='I48')].encounterId").isEmpty());
        read("doctor", WOJCIK, "diagnoses")
                .andExpect(jsonPath("$[0].status").value("resolved"));
    }

    @Test
    void allergiesExposeAtcCodesOnlyWhenPresent() throws Exception {
        read("pharmacist", KOWALSKI, "allergies")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].substance").value("Penicylina"))
                .andExpect(jsonPath("$[0].category").value("drug"))
                .andExpect(jsonPath("$[0].severity").value("life_threatening"))
                .andExpect(jsonPath("$[0].status").value("active"))
                .andExpect(jsonPath("$[0].reaction").value("Wstrząs anafilaktyczny"))
                .andExpect(jsonPath("$[0].atcCodes", contains("J01C")))
                .andExpect(jsonPath("$[0].recordedById").doesNotExist());
        // alergia bez kodow ATC: pole pomijane
        read("pharmacist", "a8500c41-1152-563d-b249-363417666099", "allergies")
                .andExpect(jsonPath("$[0].category").value("food"))
                .andExpect(jsonPath("$[0].atcCodes").doesNotExist());
        read("doctor", SZYMANSKI, "allergies").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void contraindicationsAndTreatmentsInContractShape() throws Exception {
        read("lab-tech", KOWALSKI, "contraindications")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                // najnowsze wg recordedAt: NLPZ (7204 h) przed beta-laktamami (9604 h)
                .andExpect(jsonPath("$[0].description").value("Niesteroidowe leki przeciwzapalne (NLPZ)"))
                .andExpect(jsonPath("$[0].reason").exists())
                .andExpect(jsonPath("$[0].recordedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$[0].createdAt").doesNotExist());
        read("radiologist", KOWALSKI, "treatments")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Furosemid dożylnie"))
                .andExpect(jsonPath("$[0].type").value("pharmacotherapy"))
                .andExpect(jsonPath("$[0].status").value("ongoing"))
                .andExpect(jsonPath("$[0].encounterId").value(KOWALSKI_HOSPITALIZATION))
                .andExpect(jsonPath("$[0].practitionerId").value("16259545-f97c-531d-b9cd-6ba115379372"))
                .andExpect(jsonPath("$[0].endAt").doesNotExist());
        read("doctor", "a8500c41-1152-563d-b249-363417666099", "treatments")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.type=='rehabilitation')].status").value("ongoing"));
    }

    // --- ehr-summary ---

    @Test
    void summaryIsAssembledByTheBackend() throws Exception {
        read("doctor", KOWALSKI, "ehr-summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentDiagnoses", hasSize(3)))
                .andExpect(jsonPath("$.recentDiagnoses[0].code.code").value("I50")) // najnowsza (76 h)
                .andExpect(jsonPath("$.chronicConditions", hasSize(2)))
                .andExpect(jsonPath("$.chronicConditions[*].type", contains("chronic", "chronic")))
                .andExpect(jsonPath("$.activeMedications", hasSize(2))) // zywa recepta mock (Furosemid + Polpril)
                .andExpect(jsonPath("$.activeMedications[*].drugName", contains("Furosemid", "Polpril")))
                .andExpect(jsonPath("$.recentEncounters", hasSize(3)))
                .andExpect(jsonPath("$.recentEncounters[1].id").value(KOWALSKI_HOSPITALIZATION))
                .andExpect(jsonPath("$.allergies", hasSize(1)))
                .andExpect(jsonPath("$.allergies[0].substance").value("Penicylina"));
        read("doctor", SZYMANSKI, "ehr-summary")
                .andExpect(jsonPath("$.chronicConditions", hasSize(0)))
                .andExpect(jsonPath("$.allergies", hasSize(0)))
                .andExpect(jsonPath("$.activeMedications", hasSize(0)));
    }

    @Test
    void summaryLimitsRecentDiagnosesToFiveButKeepsAllChronicConditions() throws Exception {
        for (int i = 0; i < 4; i++) {
            postAs("doctor", KOWALSKI, "diagnoses", diagnosisJson()).andExpect(status().isCreated());
        }
        read("doctor", KOWALSKI, "diagnoses").andExpect(jsonPath("$", hasSize(7)));
        read("doctor", KOWALSKI, "ehr-summary")
                .andExpect(jsonPath("$.recentDiagnoses", hasSize(5)))
                .andExpect(jsonPath("$.chronicConditions", hasSize(2)));
    }

    // --- slownik ICD-10 ---

    @Test
    void dictionaryReturnsAllCodesAsIcd10CodingsSortedByCode() throws Exception {
        mvc.perform(get("/api/v1/dictionaries/icd-10").header(HttpHeaders.AUTHORIZATION, bearer("doctor")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JSON))
                .andExpect(jsonPath("$", hasSize(40)))
                .andExpect(jsonPath("$[*].system", org.hamcrest.Matchers.everyItem(is("ICD-10"))))
                .andExpect(jsonPath("$[0].code").value("A09"))
                .andExpect(jsonPath("$[0].display").value("Biegunka i nieżyt żołądkowo-jelitowy"));
    }

    @Test
    void dictionarySearchesByCodeAndName() throws Exception {
        icd("i10").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].code").value("I10"));
        icd("J18").andExpect(jsonPath("$[0].code").value("J18.9"));
        icd("E78.0").andExpect(jsonPath("$", hasSize(1)));
        icd("cukrzyca").andExpect(jsonPath("$[0].code").value("E11"));
        icd("zawał serca").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].code").value("I21"));
        icd("serca ostry").andExpect(jsonPath("$[0].code").value("I21")); // kolejnosc tokenow bez znaczenia
        icd("brak-takiego-kodu").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void dictionarySearchIgnoresCaseAndPolishDiacritics() throws Exception {
        icd("zawal").andExpect(jsonPath("$[*].code", containsInAnyOrder("I21", "I63")));
        icd("ZAWAŁ").andExpect(jsonPath("$[*].code", containsInAnyOrder("I21", "I63")));
        icd("Zawał").andExpect(jsonPath("$[*].code", containsInAnyOrder("I21", "I63")));
        icd("niewydolnosc").andExpect(jsonPath("$[0].code").value("I50"));
        icd("ból").andExpect(jsonPath("$[*].code", containsInAnyOrder("M54.5", "R10.4")));
        icd("bol").andExpect(jsonPath("$[*].code", containsInAnyOrder("M54.5", "R10.4")));
        icd("zoladk").andExpect(jsonPath("$[*].code", containsInAnyOrder("K25", "A09"))); // żołądka, żołądkowo
        icd("zolciowa").andExpect(jsonPath("$[0].code").value("K80"));
    }

    @Test
    void dictionaryTreatsLikeWildcardsLiterally() throws Exception {
        icd("%").andExpect(jsonPath("$", hasSize(0)));
        icd("_").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void dictionaryLimitIsAppliedByServer() throws Exception {
        mvc.perform(get("/api/v1/dictionaries/icd-10").param("size", "3")
                .header(HttpHeaders.AUTHORIZATION, bearer("nurse")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].code").value("A09"));
        mvc.perform(get("/api/v1/dictionaries/icd-10").param("size", "100000")
                .header(HttpHeaders.AUTHORIZATION, bearer("nurse")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(40)));
        mvc.perform(get("/api/v1/dictionaries/icd-10").param("size", "0")
                .header(HttpHeaders.AUTHORIZATION, bearer("nurse")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        mvc.perform(get("/api/v1/dictionaries/icd-10").param("size", "abc")
                .header(HttpHeaders.AUTHORIZATION, bearer("nurse")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void dictionaryIsAvailableToEveryAuthenticatedRole() throws Exception {
        mvc.perform(get("/api/v1/dictionaries/icd-10").header(HttpHeaders.AUTHORIZATION, bearer("registrar")))
                .andExpect(status().isOk());
    }

    // --- zapis notatek ---

    @Test
    void doctorCreatesNoteAuthoredBySessionActor() throws Exception {
        String other = UUID.randomUUID().toString();
        String body = "{\"patientId\":\"" + KOWALSKI + "\",\"authorId\":\"" + other + "\",\"encounterId\":\""
                + KOWALSKI_HOSPITALIZATION + "\",\"category\":\"progress\",\"title\":\"  Obchód  \","
                + "\"content\":\"Stan stabilny.\",\"symptoms\":[\"kaszel\",\" kaszel \",\"gorączka\"]}";
        String response = mvcPost("doctor", "/api/v1/patients/" + KOWALSKI + "/clinical-notes", body)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(
                        "/api/v1/patients/" + KOWALSKI + "/clinical-notes/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.patientId").value(KOWALSKI))
                .andExpect(jsonPath("$.authorId").value(DOCTOR_STAFF)) // z tokenu, nie z zadania
                .andExpect(jsonPath("$.createdById").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.updatedById").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.encounterId").value(KOWALSKI_HOSPITALIZATION))
                .andExpect(jsonPath("$.category").value("progress"))
                .andExpect(jsonPath("$.title").value("Obchód"))
                .andExpect(jsonPath("$.content").value("Stan stabilny."))
                .andExpect(jsonPath("$.symptoms", contains("gorączka", "kaszel")))
                .andExpect(jsonPath("$.createdAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(response, "$.id");

        assertThat(jdbc.queryForObject("select count(*) from clinical_note_symptom where note_id = ?::uuid",
                Integer.class, id)).isEqualTo(2);
        read("nurse", KOWALSKI, "clinical-notes")
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].id").value(id)); // najnowsza pierwsza

        assertThat(events.stream(ClinicalNoteCreated.class)).singleElement().satisfies(e -> {
            assertThat(e.noteId().toString()).isEqualTo(id);
            assertThat(e.patientId().toString()).isEqualTo(KOWALSKI);
            assertThat(e.authorId().toString()).isEqualTo(DOCTOR_STAFF);
            assertThat(e.category()).isEqualTo(NoteCategory.PROGRESS);
        });
    }

    @Test
    void noteWithoutOptionalFieldsOmitsThem() throws Exception {
        mvcPost("doctor", "/api/v1/patients/" + KOWALSKI + "/clinical-notes", noteJson("observation"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.encounterId").doesNotExist())
                .andExpect(jsonPath("$.symptoms").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"admission", "progress", "consultation", "nursing", "observation", "discharge"})
    void doctorMayWriteEveryCategory(String category) throws Exception {
        postAs("doctor", KOWALSKI, "clinical-notes", noteJson(category))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value(category));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nursing", "observation"})
    void nurseMayWriteNursingAndObservation(String category) throws Exception {
        postAs("nurse", KOWALSKI, "clinical-notes", noteJson(category))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(NURSE_STAFF));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admission", "progress", "consultation", "discharge"})
    void nurseIsForbiddenOtherCategories(String category) throws Exception {
        postAs("nurse", KOWALSKI, "clinical-notes", noteJson(category))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(jdbc.queryForObject("select count(*) from clinical_note", Integer.class)).isEqualTo(13);
    }

    @Test
    void radiologistMayWriteOnlyConsultation() throws Exception {
        postAs("radiologist", KOWALSKI, "clinical-notes", noteJson("consultation")).andExpect(status().isCreated());
        for (String category : List.of("admission", "progress", "nursing", "observation", "discharge")) {
            postAs("radiologist", KOWALSKI, "clinical-notes", noteJson(category)).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "lab-tech", "pharmacist", "registrar"})
    void otherRolesCannotWriteNotes(String login) throws Exception {
        postAs(login, KOWALSKI, "clinical-notes", noteJson("progress")).andExpect(status().isForbidden());
    }

    @Test
    void noteForUnknownPatientIs404() throws Exception {
        postAs("doctor", UUID.randomUUID().toString(), "clinical-notes", noteJson("progress"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        postAs("doctor", "to-nie-uuid", "clinical-notes", noteJson("progress")).andExpect(status().isNotFound());
    }

    @Test
    void noteValidationFailuresAre422WithFieldErrors() throws Exception {
        postAs("doctor", KOWALSKI, "clinical-notes", "{\"title\":\"T\",\"content\":\"C\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("category"));
        postAs("doctor", KOWALSKI, "clinical-notes", "{\"category\":\"nieznana\",\"title\":\"T\",\"content\":\"C\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("category"));
        postAs("doctor", KOWALSKI, "clinical-notes", "{\"category\":\"progress\",\"title\":\" \",\"content\":\"C\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        postAs("doctor", KOWALSKI, "clinical-notes", "{\"category\":\"progress\",\"title\":\"T\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("content"));
        postAs("doctor", KOWALSKI, "clinical-notes", "{\"category\":\"progress\",\"title\":\"" + "x".repeat(201)
                + "\",\"content\":\"C\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        postAs("doctor", KOWALSKI, "clinical-notes", "{\"category\":\"progress\",\"title\":\"T\",\"content\":\"C\","
                + "\"symptoms\":[\"\"]}")
                .andExpect(status().isUnprocessableContent());
        postAs("doctor", KOWALSKI, "clinical-notes", "{\"category\":\"progress\",\"title\":\"T\",\"content\":\"C\","
                + "\"encounterId\":\"nie-uuid\"}")
                .andExpect(status().isUnprocessableContent());
        assertThat(jdbc.queryForObject("select count(*) from clinical_note", Integer.class)).isEqualTo(13);
    }

    @Test
    void notePatientIdMustMatchPathAndEncounterMustBelongToPatient() throws Exception {
        postAs("doctor", KOWALSKI, "clinical-notes", "{\"patientId\":\"" + WISNIEWSKA
                + "\",\"category\":\"progress\",\"title\":\"T\",\"content\":\"C\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("patientId"));
        postAs("doctor", KOWALSKI, "clinical-notes", "{\"category\":\"progress\",\"title\":\"T\",\"content\":\"C\","
                + "\"encounterId\":\"" + WISNIEWSKA_ENCOUNTER + "\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("encounterId"));
        postAs("doctor", KOWALSKI, "clinical-notes", "{\"category\":\"progress\",\"title\":\"T\",\"content\":\"C\","
                + "\"encounterId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void newDischargeNoteCanBeUsedAsAdmissionSummary() throws Exception {
        String response = postAs("doctor", KOWALSKI, "clinical-notes", noteJson("discharge"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String noteId = JsonPath.read(response, "$.id");
        mvcPost("doctor", "/api/v1/patients/" + KOWALSKI + "/discharge", "{\"dischargedAt\":\"2099-01-01T00:00:00Z\","
                + "\"summaryNoteId\":\"" + noteId + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("discharged"));
        // notatka innego pacjenta nadal jest odrzucana
        mvcPost("doctor", "/api/v1/patients/" + WISNIEWSKA + "/discharge", "{\"dischargedAt\":\"2099-01-01T00:00:00Z\","
                + "\"summaryNoteId\":\"" + noteId + "\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("summaryNoteId"));
    }

    // --- zapis diagnoz i alergii ---

    @Test
    void doctorRecordsDiagnosisWithDefaults() throws Exception {
        postAs("doctor", KOWALSKI, "diagnoses", "{\"diagnosedById\":\"" + UUID.randomUUID() + "\",\"encounterId\":\""
                + KOWALSKI_HOSPITALIZATION + "\",\"code\":{\"system\":\"ICD-10\",\"code\":\"J18.9\","
                + "\"display\":\"Zapalenie płuc, nieokreślone\"},\"type\":\"secondary\",\"notes\":\"  \"}")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(
                        "/api/v1/patients/" + KOWALSKI + "/diagnoses/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.code.system").value("ICD-10"))
                .andExpect(jsonPath("$.code.code").value("J18.9"))
                .andExpect(jsonPath("$.type").value("secondary"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.diagnosedById").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.createdById").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.diagnosedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.version").value(0));
        read("doctor", KOWALSKI, "diagnoses").andExpect(jsonPath("$", hasSize(4)));
        assertThat(events.stream(DiagnosisRecorded.class)).singleElement()
                .satisfies(e -> assertThat(e.code().system()).isEqualTo(CodingSystem.ICD_10));
    }

    @Test
    void diagnosisAcceptsOtherCodingSystemsAndRejectsInvalidInput() throws Exception {
        postAs("doctor", KOWALSKI, "diagnoses", "{\"code\":{\"system\":\"local\",\"code\":\"X1\",\"display\":\"Lokalny\"},"
                + "\"type\":\"chronic\",\"status\":\"resolved\",\"diagnosedAt\":\"2025-01-02T03:04:05Z\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code.system").value("local"))
                .andExpect(jsonPath("$.status").value("resolved"))
                .andExpect(jsonPath("$.diagnosedAt").value("2025-01-02T03:04:05Z"));
        postAs("doctor", KOWALSKI, "diagnoses", "{\"code\":{\"system\":\"SNOMED\",\"code\":\"X\",\"display\":\"D\"},"
                + "\"type\":\"primary\"}").andExpect(status().isUnprocessableContent());
        postAs("doctor", KOWALSKI, "diagnoses", "{\"code\":{\"system\":\"ICD-10\",\"code\":\" \",\"display\":\"D\"},"
                + "\"type\":\"primary\"}").andExpect(status().isUnprocessableContent());
        postAs("doctor", KOWALSKI, "diagnoses", "{\"type\":\"primary\"}").andExpect(status().isUnprocessableContent());
        postAs("doctor", KOWALSKI, "diagnoses", "{\"code\":{\"system\":\"ICD-10\",\"code\":\"I10\",\"display\":\"D\"}}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("type"));
        postAs("doctor", KOWALSKI, "diagnoses", "{\"encounterId\":\"" + WISNIEWSKA_ENCOUNTER
                + "\",\"code\":{\"system\":\"ICD-10\",\"code\":\"I10\",\"display\":\"D\"},\"type\":\"primary\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("encounterId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nurse", "admin", "lab-tech", "radiologist", "pharmacist", "registrar"})
    void onlyDoctorsRecordDiagnoses(String login) throws Exception {
        postAs(login, KOWALSKI, "diagnoses", diagnosisJson()).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "nurse"})
    void doctorsAndNursesRecordAllergies(String login) throws Exception {
        String staff = "doctor".equals(login) ? DOCTOR_STAFF : NURSE_STAFF;
        postAs(login, SZYMANSKI, "allergies", "{\"substance\":\" Aspiryna \",\"category\":\"drug\","
                + "\"reaction\":\"Skurcz oskrzeli\",\"severity\":\"severe\",\"recordedById\":\"" + UUID.randomUUID()
                + "\",\"atcCodes\":[\"N02BA\",\"N02BA\",\"B01AC06\"]}")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(
                        "/api/v1/patients/" + SZYMANSKI + "/allergies/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.substance").value("Aspiryna"))
                .andExpect(jsonPath("$.severity").value("severe"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.recordedById").value(staff))
                .andExpect(jsonPath("$.atcCodes", contains("B01AC06", "N02BA")))
                .andExpect(jsonPath("$.version").value(0));
        read("doctor", SZYMANSKI, "allergies").andExpect(jsonPath("$", hasSize(1)));
        assertThat(events.stream(AllergyRecorded.class)).singleElement()
                .satisfies(e -> assertThat(e.atcCodes()).containsExactlyInAnyOrder("N02BA", "B01AC06"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "lab-tech", "radiologist", "pharmacist", "registrar"})
    void otherRolesCannotRecordAllergies(String login) throws Exception {
        postAs(login, KOWALSKI, "allergies", allergyJson()).andExpect(status().isForbidden());
    }

    @Test
    void allergyValidationAndUnknownPatient() throws Exception {
        postAs("doctor", KOWALSKI, "allergies", "{\"category\":\"drug\",\"reaction\":\"R\",\"severity\":\"mild\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("substance"));
        postAs("doctor", KOWALSKI, "allergies", "{\"substance\":\"S\",\"category\":\"inne\",\"reaction\":\"R\","
                + "\"severity\":\"mild\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("category"));
        postAs("doctor", KOWALSKI, "allergies", "{\"substance\":\"S\",\"category\":\"drug\",\"reaction\":\"R\","
                + "\"severity\":\"mild\",\"atcCodes\":[\"ZBYT-DLUGI-KOD\"]}")
                .andExpect(status().isUnprocessableContent());
        postAs("doctor", KOWALSKI, "allergies", "{\"patientId\":\"" + WISNIEWSKA + "\",\"substance\":\"S\","
                + "\"category\":\"drug\",\"reaction\":\"R\",\"severity\":\"mild\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("patientId"));
        postAs("doctor", UUID.randomUUID().toString(), "allergies", allergyJson()).andExpect(status().isNotFound());
        postAs("doctor", UUID.randomUUID().toString(), "diagnoses", diagnosisJson()).andExpect(status().isNotFound());
    }

    // --- pomocnicze ---

    private ResultActions read(String login, String patientId, String resource) throws Exception {
        return mvc.perform(get("/api/v1/patients/{id}/{r}", patientId, resource)
                .header(HttpHeaders.AUTHORIZATION, bearer(login)));
    }

    private ResultActions postAs(String login, String patientId, String resource, String body) throws Exception {
        return mvcPost(login, "/api/v1/patients/" + patientId + "/" + resource, body);
    }

    private ResultActions mvcPost(String login, String url, String body) throws Exception {
        return mvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, bearer(login)).contentType(JSON)
                .content(body));
    }

    private ResultActions icd(String term) throws Exception {
        return mvc.perform(get("/api/v1/dictionaries/icd-10").param("term", term)
                .header(HttpHeaders.AUTHORIZATION, bearer("doctor"))).andExpect(status().isOk());
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

    private static String noteJson(String category) {
        return "{\"category\":\"" + category + "\",\"title\":\"Tytuł\",\"content\":\"Treść notatki.\"}";
    }

    private static String diagnosisJson() {
        return "{\"code\":{\"system\":\"ICD-10\",\"code\":\"I10\",\"display\":\"Nadciśnienie tętnicze samoistne\"},"
                + "\"type\":\"secondary\"}";
    }

    private static String allergyJson() {
        return "{\"substance\":\"Pyłki\",\"category\":\"environment\",\"reaction\":\"Katar\",\"severity\":\"mild\"}";
    }
}
