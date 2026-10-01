package robert_neat.his_backend.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.security.RolePermissions;
import robert_neat.his_backend.staff.StaffRole;

/** Kontrakt `/patients*` na danych mock: 15 pacjentow, 10 przyjec (7 aktywnych, 2 wypisane + 1 aktywne SOR bez kontaktu). */
@WithMockUser(authorities = {"patient:read", "patient:write", "admission:read", "admission:admit",
        "admission:discharge"})
class PatientApiTest extends ApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private static final String KOWALSKI = "c078186c-c437-5fa8-8a5b-a6bf8883f8bf"; // admitted, Choroby wewn., 204/A
    private static final String WISNIEWSKA = "7466c824-06b6-57f2-8bae-2f20f77d64b6"; // admitted, Kardiologia
    private static final String WOJCIK = "7e25abc6-1c68-5922-8f9b-e5a8d6eeb5c9"; // outpatient, brak przyjecia
    private static final String LEWANDOWSKI = "dfb16109-f62b-5791-a571-a3652caa0ea7"; // discharged
    private static final String ZIELINSKA = "fc5513b6-647f-51d1-8e7e-35d54d4dc21c"; // registered
    private static final String SZYMANSKI = "50c8f3fa-ea66-581a-9207-f9c4c7131d26"; // admitted, SOR, izolacja, 0+
    private static final String BONDARENKO = "2350ac70-4b44-51c1-8be3-5b736e27a2c9"; // bez PESEL, kontakt zakonczony

    private static final String CHOROBY_WEWN = "25c25490-5067-5aaa-bcf5-5dc23f56588b";
    private static final String KARDIOLOGIA = "36877e50-4f0b-5b6c-bc22-983228fe0d83";
    private static final String ANNA_NOWAK = "16259545-f97c-531d-b9cd-6ba115379372"; // doctor
    private static final String PIELEGNIARKA = "625e824c-3b63-51c2-9e56-78cbfd0ff9a5"; // nurse

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    // --- lista ---

    @Test
    void listReturnsPageOfSummariesInContractShape() throws Exception {
        mvc.perform(get("/api/v1/patients"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items", hasSize(15)))
                .andExpect(jsonPath("$.items[0].lastName").value("Bondarenko"))
                .andExpect(jsonPath("$.items[1].lastName").value("Dąbrowski"))
                .andExpect(jsonPath("$.items[2].lastName").value("Jankowski"))
                .andExpect(jsonPath("$.items[14].lastName").value("Zielińska"))
                // PatientSummary: tylko pola kontraktu
                .andExpect(jsonPath("$.items[?(@.id=='" + KOWALSKI + "')].mrn").value("HIS/2026/000001"))
                .andExpect(jsonPath("$.items[?(@.id=='" + KOWALSKI + "')].pesel").value("68031437976"))
                .andExpect(jsonPath("$.items[?(@.id=='" + KOWALSKI + "')].birthDate").value("1968-03-14"))
                .andExpect(jsonPath("$.items[?(@.id=='" + KOWALSKI + "')].gender").value("male"))
                .andExpect(jsonPath("$.items[?(@.id=='" + KOWALSKI + "')].status").value("admitted"))
                .andExpect(jsonPath("$.items[?(@.id=='" + KOWALSKI + "')].flags[0]").value("fall_risk"))
                .andExpect(jsonPath("$.items[?(@.id=='" + KOWALSKI + "')].wardName").value("Oddział Chorób Wewnętrznych"))
                .andExpect(jsonPath("$.items[?(@.id=='" + KOWALSKI + "')].bed").value("A"))
                .andExpect(jsonPath("$.items[?(@.id=='" + KOWALSKI + "')].address").isEmpty())
                // wardName/bed tylko dla `admitted`
                .andExpect(jsonPath("$.items[?(@.id=='" + WOJCIK + "')].wardName").isEmpty())
                .andExpect(jsonPath("$.items[?(@.id=='" + ZIELINSKA + "')].bed").isEmpty());
    }

    @Test
    void listPaginatesAndSorts() throws Exception {
        mvc.perform(get("/api/v1/patients").param("size", "5").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items", hasSize(5)));
        mvc.perform(get("/api/v1/patients").param("size", "5").param("page", "2"))
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[4].lastName").value("Zielińska"));
        mvc.perform(get("/api/v1/patients").param("page", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(15));
        mvc.perform(get("/api/v1/patients").param("sort", "birthDate,desc").param("size", "2"))
                .andExpect(jsonPath("$.items[0].lastName").value("Kwiatkowski"))
                .andExpect(jsonPath("$.items[1].lastName").value("Krawczyk"));
        mvc.perform(get("/api/v1/patients").param("sort", "mrn,desc").param("size", "1"))
                .andExpect(jsonPath("$.items[0].mrn").value("HIS/2026/000015"));
        mvc.perform(get("/api/v1/patients").param("sort", "pesel,asc"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    @Test
    void listFiltersByStatusAndWard() throws Exception {
        mvc.perform(get("/api/v1/patients").param("status", "admitted"))
                .andExpect(jsonPath("$.totalElements").value(8));
        mvc.perform(get("/api/v1/patients").param("status", "outpatient"))
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/v1/patients").param("status", "discharged"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/patients").param("status", "registered"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ZIELINSKA));
        mvc.perform(get("/api/v1/patients").param("wardId", CHOROBY_WEWN))
                .andExpect(jsonPath("$.totalElements").value(2)) // Kowalski i Mazur; wypisany Lewandowski odpada
                .andExpect(jsonPath("$.items[*].status").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("admitted"))));
        mvc.perform(get("/api/v1/patients").param("wardId", KARDIOLOGIA))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(WISNIEWSKA));
        mvc.perform(get("/api/v1/patients").param("wardId", CHOROBY_WEWN).param("status", "discharged"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/patients").param("status", "nieznany"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        mvc.perform(get("/api/v1/patients").param("wardId", "to-nie-uuid"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void searchIgnoresCaseAndPolishDiacritics() throws Exception {
        assertSearch("kozlowska", "Kozłowska");
        assertSearch("KOZŁOWSKA", "Kozłowska");
        assertSearch("STANISLAW", "Lewandowski"); // imie Stanisław
        assertSearch("wojcik", "Wójcik");
        assertSearch("woz", "Woźniak");
        assertSearch("dabrowski", "Dąbrowski");
        assertSearch("zielinska", "Zielińska");
        assertSearch("Zofia Zielinska", "Zielińska"); // kazdy token musi pasowac
        assertSearch("68031437", "Kowalski"); // fragment PESEL
        assertSearch("HIS/2026/000007", "Szymański"); // MRN
        assertSearch("000013", "Kwiatkowski");
    }

    @Test
    void searchWithoutMatchAndWithLikeWildcardsReturnsNothing() throws Exception {
        for (String term : List.of("zzzz", "%", "_", "Kowalski Zofia")) {
            mvc.perform(get("/api/v1/patients").param("term", term))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
        mvc.perform(get("/api/v1/patients").param("term", "  "))
                .andExpect(jsonPath("$.totalElements").value(15));
    }

    @Test
    void searchCombinesWithStatusFilterAndPaging() throws Exception {
        mvc.perform(get("/api/v1/patients").param("term", "ski").param("status", "admitted").param("size", "1"))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(1)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].status").value("admitted"));
    }

    private void assertSearch(String term, String lastName) throws Exception {
        mvc.perform(get("/api/v1/patients").param("term", term))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].lastName").value(lastName));
    }

    // --- szczegoly ---

    @Test
    void getReturnsFullPatientWithCurrentAdmission() throws Exception {
        mvc.perform(get("/api/v1/patients/{id}", KOWALSKI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(KOWALSKI))
                .andExpect(jsonPath("$.mrn").value("HIS/2026/000001"))
                .andExpect(jsonPath("$.pesel").value("68031437976"))
                .andExpect(jsonPath("$.firstName").value("Jan"))
                .andExpect(jsonPath("$.lastName").value("Kowalski"))
                .andExpect(jsonPath("$.birthDate").value("1968-03-14"))
                .andExpect(jsonPath("$.gender").value("male"))
                .andExpect(jsonPath("$.phone").value("+48 512 000 001"))
                .andExpect(jsonPath("$.email").value("jan.kowalski@example.com"))
                .andExpect(jsonPath("$.address.street").value("Kwiatowa"))
                .andExpect(jsonPath("$.address.buildingNumber").value("12"))
                .andExpect(jsonPath("$.address.apartmentNumber").value("4"))
                .andExpect(jsonPath("$.address.postalCode").value("00-001"))
                .andExpect(jsonPath("$.address.city").value("Warszawa"))
                .andExpect(jsonPath("$.address.country").value("Polska"))
                .andExpect(jsonPath("$.emergencyContact.fullName").value("Ewa Kowalska"))
                .andExpect(jsonPath("$.emergencyContact.relation").value("Żona"))
                .andExpect(jsonPath("$.emergencyContact.isLegalGuardian").value(false))
                .andExpect(jsonPath("$.insurance.status").value("active"))
                .andExpect(jsonPath("$.insurance.nfzBranch").value("07"))
                .andExpect(jsonPath("$.insurance.payer").value("NFZ"))
                .andExpect(jsonPath("$.insurance.ewusVerifiedAt", matchesPattern(".*Z$")))
                .andExpect(jsonPath("$.bloodType").value("A+"))
                .andExpect(jsonPath("$.status").value("admitted"))
                .andExpect(jsonPath("$.flags[0]").value("fall_risk"))
                .andExpect(jsonPath("$.createdAt", matchesPattern("\\d{4}-\\d\\d-\\d\\dT.*Z")))
                .andExpect(jsonPath("$.updatedAt", matchesPattern("\\d{4}-\\d\\d-\\d\\dT.*Z")))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.currentAdmission.id").value("a2c2db40-225e-5f59-9c66-31e7414b7b71"))
                .andExpect(jsonPath("$.currentAdmission.patientId").value(KOWALSKI))
                .andExpect(jsonPath("$.currentAdmission.encounterId").value("11b7050d-6f4c-546a-9bf7-da0f024b363e"))
                .andExpect(jsonPath("$.currentAdmission.status").value("active"))
                .andExpect(jsonPath("$.currentAdmission.admissionType").value("planned"))
                .andExpect(jsonPath("$.currentAdmission.wardId").value(CHOROBY_WEWN))
                .andExpect(jsonPath("$.currentAdmission.room").value("204"))
                .andExpect(jsonPath("$.currentAdmission.bed").value("A"))
                .andExpect(jsonPath("$.currentAdmission.attendingPhysicianId").value(ANNA_NOWAK))
                .andExpect(jsonPath("$.currentAdmission.referralNumber").value("SK/2026/1123"))
                .andExpect(jsonPath("$.currentAdmission.version").value(0))
                .andExpect(jsonPath("$.currentAdmission.triageLevel").doesNotExist())
                .andExpect(jsonPath("$.currentAdmission.dischargedAt").doesNotExist());
    }

    @Test
    void getEmergencyAdmissionExposesTriageAndWireEnums() throws Exception {
        mvc.perform(get("/api/v1/patients/{id}", SZYMANSKI))
                .andExpect(jsonPath("$.bloodType").value("0+"))
                .andExpect(jsonPath("$.flags[0]").value("isolation"))
                .andExpect(jsonPath("$.currentAdmission.admissionType").value("emergency"))
                .andExpect(jsonPath("$.currentAdmission.triageLevel").value("red"));
        mvc.perform(get("/api/v1/patients/{id}", "55cc6e9e-6413-58bc-88b6-6342579d8413"))
                .andExpect(jsonPath("$.bloodType").value("0-"))
                .andExpect(jsonPath("$.flags[0]").value("fall_risk"))
                .andExpect(jsonPath("$.flags[1]").value("vip"))
                .andExpect(jsonPath("$.emergencyContact.isLegalGuardian").value(true))
                .andExpect(jsonPath("$.currentAdmission.admissionType").value("transfer"));
    }

    @Test
    void getPatientWithoutPeselKeepsExplicitNullAndOmitsOtherAbsentFields() throws Exception {
        String body = mvc.perform(get("/api/v1/patients/{id}", BONDARENKO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("pesel")))
                .andExpect(jsonPath("$.pesel").value((Object) null))
                .andExpect(jsonPath("$.noPeselReason").value("foreigner"))
                .andExpect(jsonPath("$.identityDocument.type").value("passport"))
                .andExpect(jsonPath("$.identityDocument.number").value("FF1234567"))
                .andExpect(jsonPath("$.insurance.status").value("unknown"))
                .andExpect(jsonPath("$.insurance.payer").value("none"))
                .andExpect(jsonPath("$.flags", hasSize(0)))
                .andExpect(jsonPath("$", not(hasKey("email"))))
                .andExpect(jsonPath("$", not(hasKey("secondName"))))
                .andExpect(jsonPath("$", not(hasKey("emergencyContact"))))
                .andExpect(jsonPath("$", not(hasKey("bloodType"))))
                .andExpect(jsonPath("$", not(hasKey("createdById"))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // poza `pesel` w odpowiedzi nie ma literalnych null
        assertThat(body.replace("\"pesel\":null", "")).doesNotContain("null");
    }

    @Test
    void getDischargedPatientHasNoCurrentAdmission() throws Exception {
        mvc.perform(get("/api/v1/patients/{id}", LEWANDOWSKI))
                .andExpect(jsonPath("$.status").value("discharged"))
                .andExpect(jsonPath("$", not(hasKey("currentAdmission"))));
        mvc.perform(get("/api/v1/patients/{id}", WOJCIK))
                .andExpect(jsonPath("$.status").value("outpatient"))
                .andExpect(jsonPath("$", not(hasKey("currentAdmission"))));
    }

    @Test
    void getUnknownOrMalformedIdIs404Problem() throws Exception {
        mvc.perform(get("/api/v1/patients/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/patients/{id}", "pat-001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- historia przyjec ---

    @Test
    void admissionsAreListedNewestFirstWithStatusFilter() throws Exception {
        mvc.perform(get("/api/v1/patients/{id}/admissions", KOWALSKI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("active"))
                .andExpect(jsonPath("$[0].patientId").value(KOWALSKI));
        mvc.perform(get("/api/v1/patients/{id}/admissions", LEWANDOWSKI))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("discharged"))
                .andExpect(jsonPath("$[0].dischargeDisposition").value("home"))
                .andExpect(jsonPath("$[0].dischargedAt", matchesPattern(".*Z$")));
        mvc.perform(get("/api/v1/patients/{id}/admissions", LEWANDOWSKI).param("status", "active"))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/patients/{id}/admissions", LEWANDOWSKI).param("status", "discharged"))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/v1/patients/{id}/admissions", ZIELINSKA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/patients/{id}/admissions", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/patients/{id}/admissions", KOWALSKI).param("status", "zly"))
                .andExpect(status().isUnprocessableContent());
    }

    // --- duplicate-check ---

    @Test
    void duplicateCheckReturnsCandidateOr204() throws Exception {
        mvc.perform(post("/api/v1/patients/duplicate-check").contentType(JSON).content("{\"pesel\":\"68031437976\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(KOWALSKI))
                .andExpect(jsonPath("$.lastName").value("Kowalski"))
                .andExpect(jsonPath("$.status").value("admitted"))
                .andExpect(jsonPath("$.wardName").value("Oddział Chorób Wewnętrznych"));
        mvc.perform(post("/api/v1/patients/duplicate-check").contentType(JSON).content("{\"pesel\":\"11111111111\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(post("/api/v1/patients/duplicate-check").contentType(JSON).content("{\"pesel\":\"123\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("pesel"));
        mvc.perform(post("/api/v1/patients/duplicate-check").contentType(JSON).content("{}"))
                .andExpect(status().isUnprocessableContent());
    }

    // --- create ---

    @Test
    void createReturns201WithLocationMrnAndRegisteredStatus() throws Exception {
        String body = mvc.perform(post("/api/v1/patients").contentType(JSON).content(createJson("90010112345", null)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/v1/patients/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.mrn", matchesPattern("HIS/" + Year.now().getValue() + "/\\d{6}")))
                .andExpect(jsonPath("$.pesel").value("90010112345"))
                .andExpect(jsonPath("$.status").value("registered")) // `status` z zadania jest ignorowany
                .andExpect(jsonPath("$.firstName").value("Ewa"))
                .andExpect(jsonPath("$.secondName").value("Maria"))
                .andExpect(jsonPath("$.gender").value("female"))
                .andExpect(jsonPath("$.bloodType").value("AB-"))
                .andExpect(jsonPath("$.flags[0]").value("dnr"))
                .andExpect(jsonPath("$.flags[1]").value("vip"))
                .andExpect(jsonPath("$.identityDocument.type").value("id_card"))
                .andExpect(jsonPath("$.emergencyContact.isLegalGuardian").value(true))
                .andExpect(jsonPath("$.address.city").value("Łódź"))
                .andExpect(jsonPath("$.insurance.payer").value("NFZ"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt", matchesPattern(".*Z$")))
                .andExpect(jsonPath("$", not(hasKey("currentAdmission"))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/v1/patients/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Nowicka"));
        // znaleziony wyszukiwaniem po polskich znakach (nazwisko bez diakrytykow)
        mvc.perform(get("/api/v1/patients").param("term", "nowicka"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createWithoutPeselRequiresReasonAndKeepsNullPesel() throws Exception {
        mvc.perform(post("/api/v1/patients").contentType(JSON).content(createJson(null, "newborn")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasKey("pesel")))
                .andExpect(jsonPath("$.pesel").value((Object) null))
                .andExpect(jsonPath("$.noPeselReason").value("newborn"));
        mvc.perform(post("/api/v1/patients").contentType(JSON).content(createJson(null, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("noPeselReason"));
    }

    @Test
    void createWithExistingPeselIs409() throws Exception {
        mvc.perform(post("/api/v1/patients").contentType(JSON).content(createJson("68031437976", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(content().string(not(containsString("68031437976"))));
    }

    @Test
    void createValidatesFields() throws Exception {
        mvc.perform(post("/api/v1/patients").contentType(JSON).content(createJson("1234567890", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("pesel"));
        mvc.perform(post("/api/v1/patients").contentType(JSON).content(createJson("ABCDEFGHIJK", null)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/patients").contentType(JSON)
                .content(createJson("90010112345", null).replace("\"firstName\":\"Ewa\"", "\"firstName\":\"\"")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[?(@.field=='firstName')]", hasSize(1)));
        mvc.perform(post("/api/v1/patients").contentType(JSON)
                .content(createJson("90010112345", null).replace("\"street\":\"Piotrkowska\",", "")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("address.street"));
        mvc.perform(post("/api/v1/patients").contentType(JSON)
                .content(createJson("90010112345", null).replace("\"gender\":\"female\"", "\"gender\":\"x\"")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("gender"));
        mvc.perform(post("/api/v1/patients").contentType(JSON)
                .content(createJson("90010112345", null).replace("\"birthDate\":\"1990-01-01\"", "\"birthDate\":\"2999-01-01\"")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"));
        mvc.perform(post("/api/v1/patients").contentType(JSON).content("{}"))
                .andExpect(status().isUnprocessableContent());
    }

    // --- patch ---

    @Test
    void patchAppliesPartialChangesAndClearsNulls() throws Exception {
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON)
                .content("{\"version\":0,\"phone\":null,\"email\":\"nowy@example.com\",\"emergencyContact\":null,"
                        + "\"flags\":[\"vip\",\"dnr\"],\"status\":\"discharged\",\"mrn\":\"X\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(hasKey("phone"))))
                .andExpect(jsonPath("$", not(hasKey("emergencyContact"))))
                .andExpect(jsonPath("$.email").value("nowy@example.com"))
                .andExpect(jsonPath("$.flags[0]").value("dnr"))
                .andExpect(jsonPath("$.flags[1]").value("vip"))
                // brak pola = bez zmian; status i mrn ignorowane
                .andExpect(jsonPath("$.firstName").value("Jan"))
                .andExpect(jsonPath("$.address.street").value("Kwiatowa"))
                .andExpect(jsonPath("$.bloodType").value("A+"))
                .andExpect(jsonPath("$.pesel").value("68031437976"))
                .andExpect(jsonPath("$.mrn").value("HIS/2026/000001"))
                .andExpect(jsonPath("$.status").value("admitted"))
                .andExpect(jsonPath("$.currentAdmission.status").value("active"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/patients/{id}", KOWALSKI))
                .andExpect(jsonPath("$", not(hasKey("phone"))))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void patchWithStaleVersionIs409AndWithoutVersionIsAccepted() throws Exception {
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{\"version\":5,\"phone\":\"1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{\"phone\":\"+48 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+48 1"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{\"version\":0,\"phone\":\"2\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{\"version\":1,\"phone\":\"2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void patchPeselRules() throws Exception {
        // duplikat PESEL innego pacjenta
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{\"pesel\":\"55070245589\"}"))
                .andExpect(status().isConflict());
        // ten sam PESEL u tego samego pacjenta nie jest duplikatem
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{\"pesel\":\"68031437976\"}"))
                .andExpect(status().isOk());
        // wyczyszczenie PESEL bez powodu -> 422
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{\"pesel\":null}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("noPeselReason"));
        // z powodem -> pesel null w odpowiedzi
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON)
                .content("{\"pesel\":null,\"noPeselReason\":\"unknown_identity\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("pesel")))
                .andExpect(jsonPath("$.pesel").value((Object) null))
                .andExpect(jsonPath("$.noPeselReason").value("unknown_identity"));
        // nadanie PESEL czyści powód
        mvc.perform(patch("/api/v1/patients/{id}", BONDARENKO).contentType(JSON).content("{\"pesel\":\"85091212345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pesel").value("85091212345"))
                .andExpect(jsonPath("$", not(hasKey("noPeselReason"))));
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{\"pesel\":\"12\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("pesel"));
    }

    @Test
    void patchValidatesMergedState() throws Exception {
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{\"firstName\":null}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("firstName"));
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{\"address\":null}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON)
                .content("{\"address\":{\"street\":\"Nowa\"}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[?(@.field=='address.city')]", hasSize(1)));
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{\"bloodType\":\"X+\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("bloodType"));
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{\"version\":\"abc\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{\"bloodType\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(hasKey("bloodType"))));
        mvc.perform(patch("/api/v1/patients/{id}", UUID.randomUUID()).contentType(JSON).content("{}"))
                .andExpect(status().isNotFound());
        // pusty PATCH nic nie zmienia
        mvc.perform(patch("/api/v1/patients/{id}", WOJCIK).contentType(JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void patchReplacesNestedObjectsWholesale() throws Exception {
        mvc.perform(patch("/api/v1/patients/{id}", BONDARENKO).contentType(JSON)
                .content("{\"identityDocument\":null,\"insurance\":{\"status\":\"active\",\"nfzBranch\":\"07\","
                        + "\"payer\":\"private\"},\"emergencyContact\":{\"fullName\":\"A B\",\"relation\":\"Brat\","
                        + "\"phone\":\"1\",\"isLegalGuardian\":false}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(hasKey("identityDocument"))))
                .andExpect(jsonPath("$.insurance.payer").value("private"))
                .andExpect(jsonPath("$.emergencyContact.relation").value("Brat"))
                .andExpect(jsonPath("$.emergencyContact.isLegalGuardian").value(false));
    }

    // --- przyjecie ---

    @Test
    void admitCreatesAdmissionAndHospitalizationEncounterAndUpdatesStatus() throws Exception {
        String body = mvc.perform(post("/api/v1/patients/{id}/admissions", ZIELINSKA).contentType(JSON)
                .content(admitJson("planned", KARDIOLOGIA, ANNA_NOWAK, "\"room\":\"110\",\"bed\":\"B\",")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/admissions")))
                .andExpect(jsonPath("$.id").value(ZIELINSKA))
                .andExpect(jsonPath("$.status").value("admitted"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.currentAdmission.status").value("active"))
                .andExpect(jsonPath("$.currentAdmission.patientId").value(ZIELINSKA))
                .andExpect(jsonPath("$.currentAdmission.admissionType").value("planned"))
                .andExpect(jsonPath("$.currentAdmission.wardId").value(KARDIOLOGIA))
                .andExpect(jsonPath("$.currentAdmission.room").value("110"))
                .andExpect(jsonPath("$.currentAdmission.attendingPhysicianId").value(ANNA_NOWAK))
                .andExpect(jsonPath("$.currentAdmission.reason").value("Planowa diagnostyka"))
                .andExpect(jsonPath("$.currentAdmission.encounterId").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String encounterId = JsonPath.read(body, "$.currentAdmission.encounterId");
        Map<String, Object> encounter = jdbc.queryForMap(
                "select type, status, ward_id::text as ward_id, practitioner_id::text as practitioner_id, reason, "
                        + "end_at, patient_id::text as patient_id from encounter where id = ?::uuid", encounterId);
        assertThat(encounter).containsEntry("type", "hospitalization").containsEntry("status", "in_progress")
                .containsEntry("ward_id", KARDIOLOGIA).containsEntry("practitioner_id", ANNA_NOWAK)
                .containsEntry("reason", "Planowa diagnostyka").containsEntry("patient_id", ZIELINSKA);
        assertThat(encounter.get("end_at")).isNull();

        mvc.perform(get("/api/v1/patients/{id}/admissions", ZIELINSKA))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].encounterId").value(encounterId));
        mvc.perform(get("/api/v1/patients").param("wardId", KARDIOLOGIA))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[?(@.id=='" + ZIELINSKA + "')].bed").value("B"));
    }

    @Test
    void admitOutpatientCreatesVisitAndOutpatientStatus() throws Exception {
        String body = mvc.perform(post("/api/v1/patients/{id}/admissions", WOJCIK).contentType(JSON)
                .content(admitJson("outpatient", KARDIOLOGIA, ANNA_NOWAK, "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("outpatient"))
                .andExpect(jsonPath("$.currentAdmission.admissionType").value("outpatient"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String encounterId = JsonPath.read(body, "$.currentAdmission.encounterId");
        assertThat(jdbc.queryForObject("select type from encounter where id = ?::uuid", String.class, encounterId))
                .isEqualTo("visit");
        // lista z filtrem wardId obejmuje tylko `admitted`
        mvc.perform(get("/api/v1/patients").param("wardId", KARDIOLOGIA))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void admitEmergencyKeepsTriageLevelAndReadmitsDischargedPatient() throws Exception {
        mvc.perform(post("/api/v1/patients/{id}/admissions", LEWANDOWSKI).contentType(JSON)
                .content(admitJson("emergency", KARDIOLOGIA, ANNA_NOWAK, "\"triageLevel\":\"orange\",")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("admitted"))
                .andExpect(jsonPath("$.currentAdmission.triageLevel").value("orange"));
        mvc.perform(get("/api/v1/patients/{id}/admissions", LEWANDOWSKI))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("active")) // najnowsze pierwsze
                .andExpect(jsonPath("$[1].status").value("discharged"));
    }

    @Test
    void admitWhenAlreadyAdmittedIs409() throws Exception {
        mvc.perform(post("/api/v1/patients/{id}/admissions", KOWALSKI).contentType(JSON)
                .content(admitJson("planned", KARDIOLOGIA, ANNA_NOWAK, "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(post("/api/v1/patients/{id}/admissions", ZIELINSKA).contentType(JSON)
                .content(admitJson("planned", KARDIOLOGIA, ANNA_NOWAK, "")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/patients/{id}/admissions", ZIELINSKA).contentType(JSON)
                .content(admitJson("emergency", KARDIOLOGIA, ANNA_NOWAK, "")))
                .andExpect(status().isConflict());
    }

    @Test
    void admitValidatesInput() throws Exception {
        mvc.perform(post("/api/v1/patients/{id}/admissions", ZIELINSKA).contentType(JSON)
                .content(admitJson("planned", UUID.randomUUID().toString(), ANNA_NOWAK, "")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("wardId"));
        mvc.perform(post("/api/v1/patients/{id}/admissions", ZIELINSKA).contentType(JSON)
                .content(admitJson("planned", KARDIOLOGIA, PIELEGNIARKA, "")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("attendingPhysicianId"));
        mvc.perform(post("/api/v1/patients/{id}/admissions", ZIELINSKA).contentType(JSON)
                .content(admitJson("planned", KARDIOLOGIA, ANNA_NOWAK, "").replace("\"reason\":\"Planowa diagnostyka\"",
                        "\"reason\":\"\"")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        mvc.perform(post("/api/v1/patients/{id}/admissions", ZIELINSKA).contentType(JSON)
                .content(admitJson("nieznany", KARDIOLOGIA, ANNA_NOWAK, "")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("admissionType"));
        mvc.perform(post("/api/v1/patients/{id}/admissions", ZIELINSKA).contentType(JSON).content("{}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/patients/{id}/admissions", UUID.randomUUID()).contentType(JSON)
                .content(admitJson("planned", KARDIOLOGIA, ANNA_NOWAK, "")))
                .andExpect(status().isNotFound());
    }

    // --- wypis ---

    @Test
    void dischargeClosesAdmissionAndEncounterAndUpdatesStatus() throws Exception {
        mvc.perform(post("/api/v1/patients/{id}/discharge", KOWALSKI).contentType(JSON)
                .content("{\"dischargedAt\":\"" + java.time.Instant.now() + "\",\"disposition\":\"home\","
                        + "\"summaryNoteId\":\"725473be-5cdf-53a2-b7f0-d9638383648b\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("discharged"))
                .andExpect(jsonPath("$", not(hasKey("currentAdmission"))))
                .andExpect(jsonPath("$.version").value(1));
        Map<String, Object> admission = jdbc.queryForMap("select status, discharge_disposition, discharged_at, "
                + "discharge_summary_note_id::text as note, version from admission "
                + "where id = 'a2c2db40-225e-5f59-9c66-31e7414b7b71'");
        assertThat(admission).containsEntry("status", "discharged").containsEntry("discharge_disposition", "home")
                .containsEntry("note", "725473be-5cdf-53a2-b7f0-d9638383648b").containsEntry("version", 1L);
        assertThat(admission.get("discharged_at")).isNotNull();
        Map<String, Object> encounter = jdbc.queryForMap(
                "select status, end_at from encounter where id = '11b7050d-6f4c-546a-9bf7-da0f024b363e'");
        assertThat(encounter).containsEntry("status", "finished");
        assertThat(encounter.get("end_at")).isNotNull();

        mvc.perform(get("/api/v1/patients/{id}/admissions", KOWALSKI))
                .andExpect(jsonPath("$[0].status").value("discharged"))
                .andExpect(jsonPath("$[0].dischargeDisposition").value("home"))
                .andExpect(jsonPath("$[0].dischargeSummaryNoteId").value("725473be-5cdf-53a2-b7f0-d9638383648b"));
        // drugi wypis: brak aktywnego przyjecia
        mvc.perform(post("/api/v1/patients/{id}/discharge", KOWALSKI).contentType(JSON)
                .content("{\"dischargedAt\":\"" + java.time.Instant.now() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void dischargeAfterAdmitFlowEndsWithDischargedStatus() throws Exception {
        mvc.perform(post("/api/v1/patients/{id}/admissions", ZIELINSKA).contentType(JSON)
                .content(admitJson("planned", KARDIOLOGIA, ANNA_NOWAK, "")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/patients/{id}/discharge", ZIELINSKA).contentType(JSON)
                .content("{\"dischargedAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("discharged"));
        mvc.perform(get("/api/v1/patients").param("status", "discharged"))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void dischargeWithoutActiveAdmissionIs409() throws Exception {
        for (String id : List.of(ZIELINSKA, WOJCIK, LEWANDOWSKI)) {
            mvc.perform(post("/api/v1/patients/{id}/discharge", id).contentType(JSON)
                    .content("{\"dischargedAt\":\"2099-01-01T00:00:00Z\"}"))
                    .andExpect(status().isConflict());
        }
        mvc.perform(post("/api/v1/patients/{id}/discharge", UUID.randomUUID()).contentType(JSON)
                .content("{\"dischargedAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void dischargeValidatesVersionDatesAndNote() throws Exception {
        String url = "/api/v1/patients/" + WISNIEWSKA + "/discharge";
        mvc.perform(post(url).contentType(JSON).content("{\"dischargedAt\":\"2099-01-01T00:00:00Z\",\"version\":7}"))
                .andExpect(status().isConflict());
        mvc.perform(post(url).contentType(JSON).content("{\"dischargedAt\":\"2000-01-01T00:00:00Z\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("dischargedAt"));
        mvc.perform(post(url).contentType(JSON).content("{\"dischargedAt\":\"2099-01-01T00:00:00Z\","
                + "\"summaryNoteId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("summaryNoteId"));
        // notatka istnieje, ale nalezy do innego pacjenta
        mvc.perform(post(url).contentType(JSON).content("{\"dischargedAt\":\"2099-01-01T00:00:00Z\","
                + "\"summaryNoteId\":\"725473be-5cdf-53a2-b7f0-d9638383648b\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post(url).contentType(JSON).content("{\"disposition\":\"home\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post(url).contentType(JSON)
                .content("{\"dischargedAt\":\"2099-01-01T00:00:00Z\",\"disposition\":\"nieznana\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("disposition"));
        // nic nie zostalo zmienione
        mvc.perform(get("/api/v1/patients/{id}", WISNIEWSKA)).andExpect(jsonPath("$.status").value("admitted"));
    }

    @Test
    void dischargeOfAdmissionWithAlreadyFinishedEncounterKeepsEncounterEnd() throws Exception {
        // dane mock: przyjecie SOR pacjenta Bondarenko ma kontakt juz `finished`
        Object endBefore = jdbc.queryForObject(
                "select end_at from encounter where id = '50a79ab6-d77a-52cd-a64a-9851e4ee7f65'", Object.class);
        mvc.perform(post("/api/v1/patients/{id}/discharge", BONDARENKO).contentType(JSON)
                .content("{\"dischargedAt\":\"2099-01-01T00:00:00Z\",\"disposition\":\"transfer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("discharged"));
        Object endAfter = jdbc.queryForObject(
                "select end_at from encounter where id = '50a79ab6-d77a-52cd-a64a-9851e4ee7f65'", Object.class);
        assertThat(endAfter).isEqualTo(endBefore);
    }

    // --- uprawnienia i audyt ---

    @Test
    @WithAnonymousUser
    void requestsWithoutTokenAre401() throws Exception {
        mvc.perform(get("/api/v1/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/patients/{id}", KOWALSKI)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/patients").contentType(JSON).content(createJson("90010112345", null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/patients/{id}/discharge", KOWALSKI).contentType(JSON)
                .content("{\"dischargedAt\":\"2099-01-01T00:00:00Z\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void writesAreForbiddenForRolesWithoutWritePermissions() throws Exception {
        // pielegniarka: odczyt pacjenta i przyjec, bez zapisu pacjenta i przyjecia/wypisu
        mvc.perform(get("/api/v1/patients").with(as(StaffRole.NURSE))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/patients/{id}/admissions", KOWALSKI).with(as(StaffRole.NURSE)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/patients").with(as(StaffRole.NURSE)).contentType(JSON)
                .content(createJson("90010112345", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(patch("/api/v1/patients/{id}", KOWALSKI).with(as(StaffRole.DOCTOR)).contentType(JSON)
                .content("{\"phone\":\"1\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/patients/{id}/admissions", ZIELINSKA).with(as(StaffRole.NURSE)).contentType(JSON)
                .content(admitJson("planned", KARDIOLOGIA, ANNA_NOWAK, ""))).andExpect(status().isForbidden());
        // rejestrator przyjmuje, ale nie wypisuje (tylko przyjecie wg API.md par. 12)
        mvc.perform(post("/api/v1/patients/{id}/discharge", KOWALSKI).with(as(StaffRole.REGISTRAR)).contentType(JSON)
                .content("{\"dischargedAt\":\"2099-01-01T00:00:00Z\"}")).andExpect(status().isForbidden());
        // laborant: odczyt pacjenta, bez historii przyjec
        mvc.perform(get("/api/v1/patients/{id}", KOWALSKI).with(as(StaffRole.LAB_TECHNICIAN)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/patients/{id}/admissions", KOWALSKI).with(as(StaffRole.LAB_TECHNICIAN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowedRolesCanWrite() throws Exception {
        mvc.perform(post("/api/v1/patients").with(as(StaffRole.REGISTRAR)).contentType(JSON)
                .content(createJson("90010112345", null))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/patients/{id}/admissions", ZIELINSKA).with(as(StaffRole.REGISTRAR))
                .contentType(JSON).content(admitJson("planned", KARDIOLOGIA, ANNA_NOWAK, "")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/patients/{id}/discharge", ZIELINSKA).with(as(StaffRole.DOCTOR)).contentType(JSON)
                .content("{\"dischargedAt\":\"2099-01-01T00:00:00Z\"}")).andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void auditFieldsComeFromTheSessionActor() throws Exception {
        String token = tokenOf("registrar");
        String registrarId = jdbc.queryForObject("select id::text from staff_member where employee_id = 'registrar'",
                String.class);
        String body = mvc.perform(post("/api/v1/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(JSON).content(createJson("90010112345", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdById").value(registrarId))
                .andExpect(jsonPath("$.updatedById").value(registrarId))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(body, "$.id");
        mvc.perform(post("/api/v1/patients/{id}/admissions", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(JSON).content(admitJson("planned", KARDIOLOGIA, ANNA_NOWAK, "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.updatedById").value(registrarId));
    }

    // --- pomocnicze ---

    private String tokenOf(String login) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                .content("{\"employeeId\":\"" + login + "\",\"password\":\"" + login + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(response, "$.accessToken");
    }

    private static RequestPostProcessor as(StaffRole role) {
        return SecurityMockMvcRequestPostProcessors.user("test-" + role.name())
                .authorities(RolePermissions.authoritiesOf(role).stream()
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList());
    }

    private static String createJson(String pesel, String noPeselReason) {
        return """
                {"pesel":%s,"noPeselReason":%s,"identityDocument":{"type":"id_card","number":"ABC123456"},
                 "firstName":"Ewa","secondName":"Maria","lastName":"Nowicka","birthDate":"1990-01-01",
                 "gender":"female","phone":"+48 600 000 000","email":"ewa@example.com",
                 "address":{"street":"Piotrkowska","buildingNumber":"10","apartmentNumber":"5",
                            "postalCode":"90-001","city":"Łódź","country":"Polska"},
                 "emergencyContact":{"fullName":"Jan Nowicki","relation":"Mąż","phone":"+48 600 000 001",
                                     "isLegalGuardian":true},
                 "insurance":{"status":"active","nfzBranch":"05","payer":"NFZ"},
                 "bloodType":"AB-","status":"admitted","flags":["vip","dnr"]}
                """.formatted(pesel == null ? "null" : "\"" + pesel + "\"",
                noPeselReason == null ? "null" : "\"" + noPeselReason + "\"");
    }

    private static String admitJson(String type, String wardId, String physicianId, String extra) {
        return "{" + extra + "\"admissionType\":\"" + type + "\",\"admittedAt\":\"2026-09-30T08:00:00Z\","
                + "\"wardId\":\"" + wardId + "\",\"attendingPhysicianId\":\"" + physicianId + "\","
                + "\"reason\":\"Planowa diagnostyka\"}";
    }
}
