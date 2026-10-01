package robert_neat.his_backend.prescription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import robert_neat.his_backend.ApiIntegrationTest;

/**
 * `POST /drug-safety-checks` (API.md, par. 6) na danych mock: pacjent Kowalski (alergia na penicyliny J01C,
 * `life_threatening`; aktywne: Furosemid, Polpril), pacjent z alergia na ibuprofen (M01A, `moderate`), pacjent
 * na Polocard + Atoris, pacjent na Xarelto, pacjent bez recept i alergii (Szymanski).
 */
class DrugSafetyApiTest extends ApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private static final String KOWALSKI = "c078186c-c437-5fa8-8a5b-a6bf8883f8bf";
    private static final String IBUPROFEN_ALLERGIC = "7e25abc6-1c68-5922-8f9b-e5a8d6eeb5c9";
    private static final String ASPIRIN_PATIENT = "7466c824-06b6-57f2-8bae-2f20f77d64b6";
    private static final String XARELTO_PATIENT = "a8500c41-1152-563d-b249-363417666099";
    private static final String SZYMANSKI = "50c8f3fa-ea66-581a-9207-f9c4c7131d26";

    private static final String AUGMENTIN = "6c6ad052-61b0-50ce-9cfc-67f86fce09a1"; // J01CR02
    private static final String IBUPROM = "7ca7c249-f7cf-59a4-ae97-a0584f09e8ca"; // M01AE01, max 1200 mg
    private static final String FUROSEMID = "ef77115d-9ec8-5742-9217-3146231204a4";
    private static final String PANTOPRAZOL = "6cf40bff-12e3-51c1-9ced-670880e391d6";
    private static final String XARELTO = "1b72160d-900b-5776-a0e1-a81f2bfa8b29"; // interacts B01AC06
    private static final String CLEXANE = "59954ea3-2321-530c-a906-c61fb61bb20b"; // interacts B01AF01, B01AC06
    private static final String POLOCARD = "f91dec9b-e81c-5fd9-9341-c1d2738c01bf"; // B01AC06
    private static final String METFORMAX = "6ab6949a-fcf3-53bd-a0a7-d01c97995b6f"; // max 3000 mg
    private static final String PARACETAMOL = "b0407813-192e-5d3c-82ca-a95305952a14"; // max 4000 mg
    private static final String APAP = "c5f0d4fe-2c32-517a-bccd-ddcfe68c2750"; // ta sama substancja, bez limitu

    private static final Map<String, String> TOKENS = new HashMap<>();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    // --- uprawnienia ---

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/drug-safety-checks").contentType(JSON).content(single(KOWALSKI, AUGMENTIN)))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nurse", "pharmacist", "admin", "lab-tech", "radiologist", "registrar"})
    void onlyDoctorRunsChecks(String login) throws Exception {
        check(login, single(KOWALSKI, AUGMENTIN)).andExpect(status().isForbidden());
    }

    // --- alergie ---

    @Test
    void severeAllergyOnAtcPrefixIsDanger() throws Exception {
        check(single(KOWALSKI, AUGMENTIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("allergy"))
                .andExpect(jsonPath("$[0].severity").value("danger"))
                .andExpect(jsonPath("$[0].drugId").value(AUGMENTIN))
                .andExpect(jsonPath("$[0].message").value(containsString("Penicylin")));
    }

    @Test
    void moderateAllergyIsWarnAndReportedOncePerAllergy() throws Exception {
        // zgodny i ATC (M01A), i substancja (Ibuprofen) - jedno ostrzezenie
        check(single(IBUPROFEN_ALLERGIC, IBUPROM)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("allergy"))
                .andExpect(jsonPath("$[0].severity").value("warn"));
    }

    @Test
    void inactiveAllergyIsIgnored() throws Exception {
        jdbc.update("update allergy set status = 'inactive' where patient_id = ?::uuid", KOWALSKI);
        check(single(KOWALSKI, AUGMENTIN)).andExpect(jsonPath("$", hasSize(0)));
    }

    // --- duplikaty ---

    @Test
    void sameSubstanceAsActiveMedicationIsDuplicate() throws Exception {
        check(single(KOWALSKI, FUROSEMID)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("duplicate"))
                .andExpect(jsonPath("$[0].severity").value("warn"))
                .andExpect(jsonPath("$[0].drugId").value(FUROSEMID));
    }

    @Test
    void duplicateMatchesSubstanceAcrossBrandsAndWithinTheDraft() throws Exception {
        // dwie pozycje tej samej substancji w jednej recepcie: ostrzezenie przy drugiej
        check(items(KOWALSKI, PANTOPRAZOL, PANTOPRAZOL)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("duplicate"))
                .andExpect(jsonPath("$[0].message").value(containsString("Na recepcie")));
        check(items(SZYMANSKI, PARACETAMOL, APAP)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].drugId").value(APAP));
        // inny preparat tej samej substancji na aktywnej recepcie (wystawionej w tym tescie)
        issue(SZYMANSKI, APAP);
        check(single(SZYMANSKI, PARACETAMOL)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("duplicate"));
    }

    @Test
    void cancelledOrExpiredPrescriptionsDoNotCountAsActive() throws Exception {
        jdbc.update("update prescription set status = 'cancelled' where patient_id = ?::uuid", KOWALSKI);
        check(single(KOWALSKI, FUROSEMID)).andExpect(jsonPath("$", hasSize(0)));
        jdbc.update("update prescription set status = 'issued', valid_from = current_date - 9, "
                + "valid_until = current_date - 1 where patient_id = ?::uuid", XARELTO_PATIENT);
        check(single(XARELTO_PATIENT, XARELTO)).andExpect(jsonPath("$", hasSize(0)));
    }

    // --- interakcje ---

    @Test
    void interactionWithActiveMedicationInBothDirections() throws Exception {
        // Clexane wymienia ATC Xarelto (B01AF01)
        check(single(XARELTO_PATIENT, CLEXANE)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("interaction"))
                .andExpect(jsonPath("$[0].severity").value("warn"))
                .andExpect(jsonPath("$[0].drugId").value(CLEXANE))
                .andExpect(jsonPath("$[0].message").value(containsString("Xarelto")));
        // Xarelto wymienia ATC Polocard (B01AC06) - aktywny lek pacjenta
        check(single(ASPIRIN_PATIENT, XARELTO)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("interaction"))
                .andExpect(jsonPath("$[0].message").value(containsString("Polocard")));
        // odwrotnie: nowy lek (Polocard) nie ma listy, ale aktywny Xarelto go wymienia
        check(single(XARELTO_PATIENT, POLOCARD)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("interaction"))
                .andExpect(jsonPath("$[0].drugId").value(POLOCARD));
    }

    @Test
    void interactionBetweenItemsOfTheDraft() throws Exception {
        check(items(SZYMANSKI, XARELTO, POLOCARD)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("interaction"))
                .andExpect(jsonPath("$[0].drugId").value(POLOCARD))
                .andExpect(jsonPath("$[0].message").value(containsString("na tej samej recepcie")));
        check(items(SZYMANSKI, PANTOPRAZOL, FUROSEMID)).andExpect(jsonPath("$", hasSize(0)));
    }

    // --- maksymalna dawka dobowa ---

    @Test
    void dailyDoseAboveMaxIsDanger() throws Exception {
        check(withDosage(SZYMANSKI, METFORMAX, dosage("850", "mg", "QID", false, null))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("max_dose"))
                .andExpect(jsonPath("$[0].severity").value("danger"))
                .andExpect(jsonPath("$[0].drugId").value(METFORMAX))
                .andExpect(jsonPath("$[0].message").value(containsString("3400")));
        // 850 x 3 = 2550 < 3000; dokladnie na limicie (1000 x 3) tez bez ostrzezenia
        check(withDosage(SZYMANSKI, METFORMAX, dosage("850", "mg", "TID", false, null)))
                .andExpect(jsonPath("$", hasSize(0)));
        check(withDosage(SZYMANSKI, METFORMAX, dosage("1000", "mg", "TID", false, null)))
                .andExpect(jsonPath("$", hasSize(0)));
        // Q4H = 6x, Q6H = 4x, Q8H = 3x, Q12H = 2x
        check(withDosage(SZYMANSKI, PARACETAMOL, dosage("700", "mg", "Q4H", false, null)))
                .andExpect(jsonPath("$[0].type").value("max_dose")); // 4200
        check(withDosage(SZYMANSKI, PARACETAMOL, dosage("1000", "mg", "Q6H", false, null)))
                .andExpect(jsonPath("$", hasSize(0))); // 4000
        check(withDosage(SZYMANSKI, PARACETAMOL, dosage("1500", "mg", "Q8H", false, null)))
                .andExpect(jsonPath("$[0].type").value("max_dose")); // 4500
        check(withDosage(SZYMANSKI, PARACETAMOL, dosage("2001", "mg", "Q12H", false, null)))
                .andExpect(jsonPath("$[0].type").value("max_dose")); // 4002
    }

    @Test
    void weeklyDoseIsAveragedPerDay() throws Exception {
        check(withDosage(SZYMANSKI, METFORMAX, dosage("21000", "mg", "QW", false, null)))
                .andExpect(jsonPath("$", hasSize(0))); // 3000 / dobe
        check(withDosage(SZYMANSKI, METFORMAX, dosage("22000", "mg", "QW", false, null)))
                .andExpect(jsonPath("$[0].type").value("max_dose")); // ~3143
    }

    @Test
    void asNeededDosageUsesMaxPerDayAsWorstCase() throws Exception {
        check(withDosage(SZYMANSKI, PARACETAMOL, dosage("1000", "mg", "PRN", true, 5)))
                .andExpect(jsonPath("$[0].type").value("max_dose")); // 5000
        check(withDosage(SZYMANSKI, PARACETAMOL, dosage("1000", "mg", "PRN", true, 4)))
                .andExpect(jsonPath("$", hasSize(0)));
        // PRN bez maxPerDay: pojedyncza dawka
        check(withDosage(SZYMANSKI, PARACETAMOL, dosage("5000", "mg", "PRN", true, null)))
                .andExpect(jsonPath("$[0].type").value("max_dose"));
    }

    @Test
    void maxDoseIsNotCheckedWithoutDosageUnlimitedDrugOrDifferentUnit() throws Exception {
        check(single(SZYMANSKI, METFORMAX)).andExpect(jsonPath("$", hasSize(0)));
        check(withDosage(SZYMANSKI, APAP, dosage("9999", "mg", "QID", false, null)))
                .andExpect(jsonPath("$", hasSize(0))); // brak maxDailyDose
        check(withDosage(SZYMANSKI, METFORMAX, dosage("1", "g", "QID", false, null)))
                .andExpect(jsonPath("$", hasSize(0))); // inna jednostka - bez przeliczen
        check(withDosage(SZYMANSKI, METFORMAX, dosage("900", "MG", "QID", false, null)))
                .andExpect(jsonPath("$[0].type").value("max_dose")); // wielkosc liter jednostki bez znaczenia
    }

    @Test
    void warningsAreOrderedPerItemByType() throws Exception {
        // alergia (M01A), a potem przekroczenie dawki (3 x 600 = 1800 > 1200)
        check(withDosage(IBUPROFEN_ALLERGIC, IBUPROM, dosage("600", "mg", "TID", false, null)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("allergy"))
                .andExpect(jsonPath("$[1].type").value("max_dose"));
    }

    @Test
    void cleanCheckReturnsEmptyArrayAndContractShapeIsStable() throws Exception {
        check(single(KOWALSKI, PANTOPRAZOL)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        String json = check(single(KOWALSKI, AUGMENTIN)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> warning = JsonPath.read(json, "$[0]");
        assertThat(warning.keySet()).containsExactlyInAnyOrder("type", "severity", "drugId", "message");
    }

    // --- bledy ---

    @Test
    void unknownPatientOrDrugIs404() throws Exception {
        check(single(UUID.randomUUID().toString(), AUGMENTIN)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        check(single(KOWALSKI, UUID.randomUUID().toString())).andExpect(status().isNotFound());
        check(items(KOWALSKI, AUGMENTIN, UUID.randomUUID().toString())).andExpect(status().isNotFound());
    }

    @Test
    void invalidRequestIs422() throws Exception {
        check("{\"drugId\":\"" + AUGMENTIN + "\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("patientId"));
        check("{\"patientId\":\"" + KOWALSKI + "\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("drugId"));
        check("{\"patientId\":\"" + KOWALSKI + "\",\"items\":[]}").andExpect(status().isUnprocessableContent());
        check(withDosage(KOWALSKI, METFORMAX, dosage("0", "mg", "QD", false, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("dosage.dose"));
        check("{\"patientId\":\"nie-uuid\",\"drugId\":\"" + AUGMENTIN + "\"}")
                .andExpect(status().isUnprocessableContent());
    }

    // --- pomocnicze ---

    private static String single(String patientId, String drugId) {
        return "{\"patientId\":\"" + patientId + "\",\"drugId\":\"" + drugId + "\"}";
    }

    private static String items(String patientId, String... drugIds) {
        StringBuilder items = new StringBuilder();
        for (String drugId : drugIds) {
            items.append(items.isEmpty() ? "" : ",").append("{\"drugId\":\"").append(drugId).append("\"}");
        }
        return "{\"patientId\":\"" + patientId + "\",\"items\":[" + items + "]}";
    }

    private static String withDosage(String patientId, String drugId, String dosage) {
        return "{\"patientId\":\"" + patientId + "\",\"drugId\":\"" + drugId + "\",\"dosage\":" + dosage + "}";
    }

    private static String dosage(String dose, String unit, String frequency, boolean asNeeded, Integer maxPerDay) {
        return "{\"dose\":" + dose + ",\"doseUnit\":\"" + unit + "\",\"route\":\"oral\",\"frequency\":\""
                + frequency + "\",\"durationDays\":7,\"asNeeded\":" + asNeeded
                + (maxPerDay == null ? "" : ",\"maxPerDay\":" + maxPerDay) + "}";
    }

    /** Wystawia (jako lekarz) aktywna recepte z jednym lekiem, zeby uzyskac aktywny lek pacjenta. */
    private void issue(String patientId, String drugId) throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        String body = "{\"validFrom\":\"" + today + "\",\"validUntil\":\"" + today.plusDays(30)
                + "\",\"kind\":\"hospital_order\",\"items\":[{\"drugId\":\"" + drugId
                + "\",\"dosage\":{\"dose\":500,\"doseUnit\":\"mg\",\"route\":\"oral\",\"frequency\":\"TID\","
                + "\"durationDays\":5,\"asNeeded\":false},\"quantityPackages\":1,\"reimbursement\":\"none\","
                + "\"substitutionAllowed\":true}]}";
        as("doctor", post("/api/v1/patients/{id}/prescriptions", patientId).contentType(JSON).content(body))
                .andExpect(status().isCreated());
    }

    private ResultActions check(String body) throws Exception {
        return check("doctor", body);
    }

    private ResultActions check(String login, String body) throws Exception {
        return as(login, post("/api/v1/drug-safety-checks").contentType(JSON).content(body));
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
