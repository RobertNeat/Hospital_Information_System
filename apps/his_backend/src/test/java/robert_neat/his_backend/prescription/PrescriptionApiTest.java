package robert_neat.his_backend.prescription;

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
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.prescription.events.PrescriptionCancelled;
import robert_neat.his_backend.prescription.events.PrescriptionIssued;

/**
 * Kontrakt recept (API.md, par. 6) na danych mock: 8 recept (5 issued, 1 expired, 2 dispensed; 6 hospital_order,
 * 2 e_prescription), 10 pozycji. Tokeny z `POST /auth/login`; aktor pochodzi z tokenu.
 */
@RecordApplicationEvents
class PrescriptionApiTest extends ApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    // recepty mock (pacjent / wystawiajacy / status)
    private static final String RX_KOWALSKI = "75f6263c-d428-593d-95e7-94a0e15cfc2c"; // issued, Furosemid + Polpril
    private static final String RX_KOWALSKI_EXPIRED = "83e7df14-c19a-5f9b-b43f-70ed69178558"; // expired, Metformax
    private static final String RX_ASPIRIN = "2dfc74df-8654-5e27-af87-3c315a71d733"; // issued, Polocard + Atoris
    private static final String RX_XARELTO = "d1649347-8cd7-5a2c-9257-77c333ee9fc1"; // issued
    private static final String RX_VENTOLIN = "6fda3a2b-0088-5373-abfb-ba37847eb915"; // dispensed, e_prescription
    private static final String RX_PANTOPRAZOL = "f835adc2-77b2-50b2-bc45-c75c64b817af"; // issued
    private static final String RX_XARELTO_DISPENSED = "c1889cbc-3815-50ce-b07c-d04a8fff2b4e"; // dispensed
    private static final String RX_FUROSEMID_20 = "226584cf-e773-55e1-a2f6-1ce357de1574"; // issued, uwagi

    private static final String KOWALSKI = "c078186c-c437-5fa8-8a5b-a6bf8883f8bf";
    private static final String ASPIRIN_PATIENT = "7466c824-06b6-57f2-8bae-2f20f77d64b6";
    private static final String VENTOLIN_PATIENT = "c04f7300-5f2c-51a2-b2b3-b59149f61c47";
    private static final String SZYMANSKI = "50c8f3fa-ea66-581a-9207-f9c4c7131d26"; // brak recept i alergii
    private static final String KOWALSKI_HOSPITALIZATION = "11b7050d-6f4c-546a-9bf7-da0f024b363e";
    private static final String WISNIEWSKA_ENCOUNTER = "2e790042-5ce2-543b-baee-416bf421230d";

    private static final String PRESCRIBER_1 = "16259545-f97c-531d-b9cd-6ba115379372"; // 4 recepty
    private static final String DOCTOR_STAFF = "3bc5ba72-1a62-3681-ba58-fa6c83501852";
    private static final String NURSE_STAFF = "28222254-25c0-34db-938f-9228b7a20c52";

    private static final String POLPRIL = "24c2f3ff-06d4-53d2-9bc5-561b36774246"; // oral; 30%, none
    private static final String FUROSEMID = "ef77115d-9ec8-5742-9217-3146231204a4";
    private static final String CLEXANE = "59954ea3-2321-530c-a906-c61fb61bb20b"; // sc; 100%, none

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
        mvc.perform(get("/api/v1/prescriptions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/prescriptions/{id}", RX_KOWALSKI)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patients/{id}/active-medications", KOWALSKI)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/patients/{id}/prescriptions", KOWALSKI).contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/prescriptions/{id}/cancel", RX_KOWALSKI)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "nurse", "pharmacist", "admin"})
    void readRolesSeePrescriptions(String login) throws Exception {
        as(login, get("/api/v1/prescriptions")).andExpect(status().isOk());
        as(login, get("/api/v1/prescriptions/{id}", RX_KOWALSKI)).andExpect(status().isOk());
        as(login, get("/api/v1/patients/{id}/active-medications", KOWALSKI)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lab-tech", "radiologist", "registrar"})
    void otherRolesCannotReadPrescriptions(String login) throws Exception {
        as(login, get("/api/v1/prescriptions")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as(login, get("/api/v1/prescriptions/{id}", RX_KOWALSKI)).andExpect(status().isForbidden());
        as(login, get("/api/v1/patients/{id}/active-medications", KOWALSKI)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nurse", "pharmacist", "admin", "lab-tech", "radiologist", "registrar"})
    void onlyDoctorIssuesAndCancels(String login) throws Exception {
        issue(login, SZYMANSKI, validBody(POLPRIL)).andExpect(status().isForbidden());
        cancel(login, RX_KOWALSKI, "{}").andExpect(status().isForbidden());
        assertThat(count()).isEqualTo(8);
    }

    // --- lista ---

    @Test
    void listReturnsAllPrescriptionsNewestFirstWithPageEnvelope() throws Exception {
        list("").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(8)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[*].id", contains(RX_ASPIRIN, RX_PANTOPRAZOL, RX_KOWALSKI, RX_XARELTO,
                        RX_FUROSEMID_20, RX_XARELTO_DISPENSED, RX_VENTOLIN, RX_KOWALSKI_EXPIRED)))
                .andExpect(jsonPath("$.items[0].items", hasSize(2)));
    }

    @Test
    void listFilters() throws Exception {
        list("patientId=" + KOWALSKI).andExpect(jsonPath("$.items[*].id", contains(RX_KOWALSKI, RX_KOWALSKI_EXPIRED)));
        list("patientId=" + UUID.randomUUID()).andExpect(jsonPath("$.totalElements").value(0));
        list("prescriberId=" + PRESCRIBER_1).andExpect(jsonPath("$.items[*].id", containsInAnyOrder(RX_KOWALSKI,
                RX_KOWALSKI_EXPIRED, RX_VENTOLIN, RX_FUROSEMID_20)));
        list("status=issued").andExpect(jsonPath("$.totalElements").value(5));
        list("status=expired").andExpect(jsonPath("$.items[*].id", contains(RX_KOWALSKI_EXPIRED)));
        list("status=dispensed").andExpect(jsonPath("$.items[*].id", contains(RX_XARELTO_DISPENSED, RX_VENTOLIN)));
        list("status=cancelled").andExpect(jsonPath("$.totalElements").value(0));
        list("status=partially_dispensed").andExpect(jsonPath("$.totalElements").value(0));
        list("kind=e_prescription").andExpect(jsonPath("$.items[*].id", contains(RX_VENTOLIN, RX_KOWALSKI_EXPIRED)));
        list("kind=hospital_order").andExpect(jsonPath("$.totalElements").value(6));
        list("patientId=" + KOWALSKI + "&status=issued").andExpect(jsonPath("$.items[*].id", contains(RX_KOWALSKI)));
        list("prescriberId=" + PRESCRIBER_1 + "&kind=e_prescription&status=dispensed")
                .andExpect(jsonPath("$.items[*].id", contains(RX_VENTOLIN)));
    }

    @Test
    void listPaginationAndSorting() throws Exception {
        list("size=3&page=0").andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(8)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].id").value(RX_ASPIRIN));
        list("size=3&page=2").andExpect(jsonPath("$.items", hasSize(2))).andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.items[1].id").value(RX_KOWALSKI_EXPIRED));
        list("sort=validFrom,asc&size=2").andExpect(jsonPath("$.items[*].id",
                contains(RX_KOWALSKI_EXPIRED, RX_VENTOLIN)));
        list("sort=validUntil,desc&size=1").andExpect(jsonPath("$.items[0].id").value(RX_ASPIRIN));
        list("sort=issuedAt,asc&size=1").andExpect(jsonPath("$.items[0].id").value(RX_KOWALSKI_EXPIRED));
        list("sort=status,asc").andExpect(status().isOk());
        list("sort=kind,desc").andExpect(status().isOk());
    }

    @Test
    void listRejectsUnknownSortAndBadFilterValues() throws Exception {
        list("sort=accessCode,asc").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        list("status=wydana").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("partially_dispensed")));
        list("kind=inna").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("kind"));
        list("patientId=nie-uuid").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("patientId"));
    }

    // --- szczegoly i kontrakt JSON ---

    @Test
    void detailMatchesContract() throws Exception {
        LocalDate today = today();
        as("pharmacist", get("/api/v1/prescriptions/{id}", RX_KOWALSKI)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RX_KOWALSKI))
                .andExpect(jsonPath("$.patientId").value(KOWALSKI))
                .andExpect(jsonPath("$.prescriberId").value(PRESCRIBER_1))
                .andExpect(jsonPath("$.kind").value("hospital_order"))
                .andExpect(jsonPath("$.status").value("issued"))
                .andExpect(jsonPath("$.validFrom").value(today.minusDays(3).toString()))
                .andExpect(jsonPath("$.validUntil").value(today.plusDays(27).toString()))
                .andExpect(jsonPath("$.accessCode").value("4821"))
                .andExpect(jsonPath("$.eRxKey").value("254AEYJLX5EJ0R5MFPACPM5GZUIJMPG97MK1VAQBRLVS"))
                .andExpect(jsonPath("$.issuedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.items", hasSize(2)))
                // pozycje wg nazwy leku, potem id
                .andExpect(jsonPath("$.items[*].drugName", contains("Furosemid", "Polpril")))
                .andExpect(jsonPath("$.items[0].drugId").value(FUROSEMID))
                .andExpect(jsonPath("$.items[0].activeSubstance").value("Furosemid"))
                .andExpect(jsonPath("$.items[0].strength").value("40 mg"))
                .andExpect(jsonPath("$.items[0].form").value("tablet"))
                .andExpect(jsonPath("$.items[0].quantityPackages").value(1))
                .andExpect(jsonPath("$.items[0].reimbursement").value("R"))
                .andExpect(jsonPath("$.items[0].substitutionAllowed").value(true))
                .andExpect(jsonPath("$.items[0].dosage.dose").value(40))
                .andExpect(jsonPath("$.items[0].dosage.doseUnit").value("mg"))
                .andExpect(jsonPath("$.items[0].dosage.route").value("oral"))
                .andExpect(jsonPath("$.items[0].dosage.frequency").value("BID"))
                .andExpect(jsonPath("$.items[0].dosage.durationDays").value(14))
                .andExpect(jsonPath("$.items[0].dosage.asNeeded").value(false))
                .andExpect(jsonPath("$.items[0].dosage.instructions").value("Rano i w południe"))
                .andExpect(jsonPath("$.items[1].reimbursement").value("30%"));
    }

    @Test
    void jsonOmitsAbsentOptionalFieldsAndKeepsRequiredOnes() throws Exception {
        String json = as("doctor", get("/api/v1/prescriptions/{id}", RX_KOWALSKI)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> rx = JsonPath.read(json, "$");
        assertThat(rx.keySet()).contains("id", "patientId", "prescriberId", "issuedAt", "validFrom", "validUntil",
                "kind", "items", "status", "accessCode", "eRxKey", "version", "createdAt", "updatedAt")
                .doesNotContain("encounterId", "notes", "cancelledAt", "cancelReason", "createdById", "updatedById");
        Map<String, Object> item = JsonPath.read(json, "$.items[0]");
        assertThat(item.keySet()).containsExactlyInAnyOrder("id", "drugId", "drugName", "activeSubstance",
                "strength", "form", "dosage", "quantityPackages", "reimbursement", "substitutionAllowed");
        Map<String, Object> dosage = JsonPath.read(json, "$.items[0].dosage");
        assertThat(dosage.keySet()).containsExactlyInAnyOrder("dose", "doseUnit", "route", "frequency",
                "durationDays", "asNeeded", "instructions"); // timesOfDay i maxPerDay pomijane, gdy brak
        // pozycja z porami dnia, notatka na recepcie
        as("doctor", get("/api/v1/prescriptions/{id}", RX_ASPIRIN))
                .andExpect(jsonPath("$.items[?(@.drugName=='Atoris')].dosage.timesOfDay[*]", contains("evening")))
                .andExpect(jsonPath("$.items[?(@.drugName=='Polocard')].dosage.timesOfDay").isEmpty());
        as("doctor", get("/api/v1/prescriptions/{id}", RX_FUROSEMID_20))
                .andExpect(jsonPath("$.notes").value("Dawka zredukowana ze względu na funkcję nerek."))
                .andExpect(jsonPath("$.items[0].dosage.dose").value(20));
        // dawka doraznie: maxPerDay, liczba calkowita jako number, enumy `inhaler`/`PRN`/`inhalation`
        as("doctor", get("/api/v1/prescriptions/{id}", RX_VENTOLIN))
                .andExpect(jsonPath("$.kind").value("e_prescription"))
                .andExpect(jsonPath("$.status").value("dispensed"))
                .andExpect(jsonPath("$.items[0].form").value("inhaler"))
                .andExpect(jsonPath("$.items[0].dosage.frequency").value("PRN"))
                .andExpect(jsonPath("$.items[0].dosage.route").value("inhalation"))
                .andExpect(jsonPath("$.items[0].dosage.asNeeded").value(true))
                .andExpect(jsonPath("$.items[0].dosage.maxPerDay").value(8))
                .andExpect(jsonPath("$.items[0].dosage.dose").value(1))
                .andExpect(jsonPath("$.items[0].strength").value("100 mcg/dawkę"));
    }

    @Test
    void detailReturnsSnapshotNotCurrentCatalog() throws Exception {
        jdbc.update("update drug set name = 'Zmieniona nazwa' where id = ?::uuid", FUROSEMID);
        as("doctor", get("/api/v1/prescriptions/{id}", RX_KOWALSKI))
                .andExpect(jsonPath("$.items[0].drugName").value("Furosemid"));
    }

    @Test
    void detailOfUnknownPrescriptionIs404() throws Exception {
        as("doctor", get("/api/v1/prescriptions/{id}", UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        as("doctor", get("/api/v1/prescriptions/{id}", "to-nie-uuid")).andExpect(status().isNotFound());
    }

    // --- wygasniecie (wyliczane przy odczycie) ---

    @Test
    void openPrescriptionPastValidUntilIsReportedAsExpired() throws Exception {
        // przed pierwszym odczytem encji w tej transakcji
        jdbc.update("update prescription set valid_from = current_date - 10, valid_until = current_date - 1 "
                + "where id = ?::uuid", RX_KOWALSKI);
        as("doctor", get("/api/v1/prescriptions/{id}", RX_KOWALSKI)).andExpect(jsonPath("$.status").value("expired"));
        list("status=expired").andExpect(jsonPath("$.items[*].id", containsInAnyOrder(RX_KOWALSKI,
                RX_KOWALSKI_EXPIRED)));
        list("status=issued").andExpect(jsonPath("$.totalElements").value(4));
        as("doctor", get("/api/v1/patients/{id}/active-medications", KOWALSKI)).andExpect(jsonPath("$", hasSize(0)));
        as("doctor", get("/api/v1/patients/{id}/ehr-summary", KOWALSKI))
                .andExpect(jsonPath("$.activeMedications", hasSize(0)));
        cancel("doctor", RX_KOWALSKI, "{}").andExpect(status().isConflict());
        // zapisany status nie jest zmieniany
        assertThat(jdbc.queryForObject("select status from prescription where id = ?::uuid", String.class,
                RX_KOWALSKI)).isEqualTo("issued");
    }

    @Test
    void validUntilTodayIsStillActive() throws Exception {
        jdbc.update("update prescription set valid_from = current_date - 10, valid_until = current_date "
                + "where id = ?::uuid", RX_KOWALSKI);
        as("doctor", get("/api/v1/prescriptions/{id}", RX_KOWALSKI)).andExpect(jsonPath("$.status").value("issued"));
        as("doctor", get("/api/v1/patients/{id}/active-medications", KOWALSKI)).andExpect(jsonPath("$", hasSize(2)));
    }

    // --- aktywne leki i ehr-summary ---

    @Test
    void activeMedicationsAreItemsOfLivePrescriptionsTaggedWithOrigin() throws Exception {
        as("nurse", get("/api/v1/patients/{id}/active-medications", KOWALSKI)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].drugName", contains("Furosemid", "Polpril")))
                .andExpect(jsonPath("$[*].prescriptionId", contains(RX_KOWALSKI, RX_KOWALSKI)))
                .andExpect(jsonPath("$[0].date").value(today().minusDays(3).toString()))
                .andExpect(jsonPath("$[0].dosage.frequency").value("BID"))
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].substitutionAllowed").value(true));
        as("doctor", get("/api/v1/patients/{id}/active-medications", ASPIRIN_PATIENT))
                .andExpect(jsonPath("$[*].drugName", contains("Atoris", "Polocard")))
                .andExpect(jsonPath("$[1].reimbursement").value("R"))
                .andExpect(jsonPath("$[1].substitutionAllowed").value(false));
        // wydana albo brak recept - brak aktywnych lekow
        as("doctor", get("/api/v1/patients/{id}/active-medications", VENTOLIN_PATIENT))
                .andExpect(jsonPath("$", hasSize(0)));
        as("doctor", get("/api/v1/patients/{id}/active-medications", SZYMANSKI)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void activeMedicationsOf404() throws Exception {
        as("doctor", get("/api/v1/patients/{id}/active-medications", UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        as("doctor", get("/api/v1/patients/{id}/active-medications", "to-nie-uuid")).andExpect(status().isNotFound());
    }

    @Test
    void ehrSummaryContainsActiveMedicationsExactlyOnce() throws Exception {
        as("doctor", get("/api/v1/patients/{id}/ehr-summary", KOWALSKI)).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeMedications", hasSize(2)))
                .andExpect(jsonPath("$.activeMedications[*].drugName", contains("Furosemid", "Polpril")))
                .andExpect(jsonPath("$.activeMedications[0].prescriptionId").value(RX_KOWALSKI))
                .andExpect(jsonPath("$.activeMedications[0].dosage.route").value("oral"));
        as("doctor", get("/api/v1/patients/{id}/ehr-summary", SZYMANSKI))
                .andExpect(jsonPath("$.activeMedications", hasSize(0)));
    }

    // --- wystawienie ---

    @Test
    void issueStoresSnapshotCodesAndActorFromToken() throws Exception {
        jdbc.update("update drug set name = 'Nazwa w katalogu' where id = ?::uuid", POLPRIL);
        LocalDate today = today();
        String body = """
                {"patientId":"%s","encounterId":"%s","prescriberId":"%s","validFrom":"%s","validUntil":"%s",
                 "kind":"e_prescription","notes":"  Kontrola ciśnienia  ","status":"dispensed","accessCode":"0000",
                 "eRxKey":"X","version":9,
                 "items":[{"id":"%s","drugId":"%s","drugName":"Nazwa od klienta","activeSubstance":"Ktoś","strength":"1 kg",
                           "form":"syrup",
                           "dosage":{"dose":2.5,"doseUnit":" mg ","route":"oral","frequency":"BID",
                                     "timesOfDay":["evening","morning","morning"],"durationDays":30,"asNeeded":false,
                                     "maxPerDay":4,"instructions":"  Po posiłku "},
                           "quantityPackages":2,"reimbursement":"30%%","substitutionAllowed":true}]}
                """.formatted(KOWALSKI, KOWALSKI_HOSPITALIZATION, NURSE_STAFF, today, today.plusDays(30),
                UUID.randomUUID(), POLPRIL);
        String response = issue("doctor", KOWALSKI, body)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        matchesPattern("/api/v1/prescriptions/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.patientId").value(KOWALSKI))
                .andExpect(jsonPath("$.encounterId").value(KOWALSKI_HOSPITALIZATION))
                .andExpect(jsonPath("$.prescriberId").value(DOCTOR_STAFF)) // z tokenu, nie z zadania
                .andExpect(jsonPath("$.createdById").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.status").value("issued")) // status z zadania ignorowany
                .andExpect(jsonPath("$.kind").value("e_prescription"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.issuedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.validFrom").value(today.toString()))
                .andExpect(jsonPath("$.validUntil").value(today.plusDays(30).toString()))
                .andExpect(jsonPath("$.accessCode", matchesPattern("\\d{4}")))
                .andExpect(jsonPath("$.eRxKey", matchesPattern("[A-Z0-9]{44}")))
                .andExpect(jsonPath("$.notes").value("Kontrola ciśnienia"))
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").isString())
                .andExpect(jsonPath("$.items[0].drugId").value(POLPRIL))
                .andExpect(jsonPath("$.items[0].drugName").value("Nazwa w katalogu")) // snapshot z katalogu
                .andExpect(jsonPath("$.items[0].activeSubstance").value("Ramipril"))
                .andExpect(jsonPath("$.items[0].strength").value("5 mg"))
                .andExpect(jsonPath("$.items[0].form").value("tablet"))
                .andExpect(jsonPath("$.items[0].dosage.dose").value(2.5))
                .andExpect(jsonPath("$.items[0].dosage.doseUnit").value("mg"))
                .andExpect(jsonPath("$.items[0].dosage.timesOfDay[*]", contains("morning", "evening")))
                .andExpect(jsonPath("$.items[0].dosage.maxPerDay").value(4))
                .andExpect(jsonPath("$.items[0].dosage.instructions").value("Po posiłku"))
                .andExpect(jsonPath("$.items[0].quantityPackages").value(2))
                .andExpect(jsonPath("$.items[0].reimbursement").value("30%"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(response, "$.id");

        // kolumny char(4) / char(44) i pory dnia zapisane; snapshot odporny na zmiany katalogu
        assertThat(jdbc.queryForObject("select access_code from prescription where id = ?::uuid", String.class, id))
                .hasSize(4).containsOnlyDigits();
        assertThat(jdbc.queryForObject("select length(erx_key) from prescription where id = ?::uuid", Integer.class,
                id)).isEqualTo(44);
        assertThat(jdbc.queryForObject("select count(*) from prescription_item_time_of_day t join prescription_item i "
                + "on i.id = t.item_id where i.prescription_id = ?::uuid", Integer.class, id)).isEqualTo(2);
        jdbc.update("update drug set name = 'Jeszcze inna' where id = ?::uuid", POLPRIL);
        as("pharmacist", get("/api/v1/prescriptions/{id}", id))
                .andExpect(jsonPath("$.items[0].drugName").value("Nazwa w katalogu"));
        list("patientId=" + KOWALSKI + "&status=issued").andExpect(jsonPath("$.totalElements").value(2));

        // nowa recepta jest od razu aktywna (ehr-summary i active-medications)
        as("doctor", get("/api/v1/patients/{id}/active-medications", KOWALSKI)).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.prescriptionId=='" + id + "')].drugName", contains("Nazwa w katalogu")));
        as("doctor", get("/api/v1/patients/{id}/ehr-summary", KOWALSKI))
                .andExpect(jsonPath("$.activeMedications", hasSize(3)));

        assertThat(events.stream(PrescriptionIssued.class)).singleElement().satisfies(e -> {
            assertThat(e.prescriptionId().toString()).isEqualTo(id);
            assertThat(e.prescriberId().toString()).isEqualTo(DOCTOR_STAFF);
            assertThat(e.itemCount()).isEqualTo(1);
            assertThat(e.kind()).isEqualTo(PrescriptionKind.E_PRESCRIPTION);
        });
    }

    @Test
    void issueWithoutOptionalFieldsOmitsThemAndHandlesPastDates() throws Exception {
        LocalDate today = today();
        String body = validBody(POLPRIL).replace(today.plusDays(30).toString(), today.minusDays(1).toString())
                .replace(today.toString(), today.minusDays(10).toString());
        String response = issue("doctor", SZYMANSKI, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.encounterId").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.items[0].dosage.timesOfDay").doesNotExist())
                .andExpect(jsonPath("$.items[0].dosage.maxPerDay").doesNotExist())
                .andExpect(jsonPath("$.items[0].dosage.instructions").doesNotExist())
                // termin w przeszlosci: wygasla od razu
                .andExpect(jsonPath("$.status").value("expired"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(response, "$.id");
        assertThat(jdbc.queryForObject("select status from prescription where id = ?::uuid", String.class, id))
                .isEqualTo("issued");
        as("doctor", get("/api/v1/patients/{id}/active-medications", SZYMANSKI)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void issuedCodesAreRandomFormatCompliant() throws Exception {
        for (int i = 0; i < 5; i++) {
            issue("doctor", SZYMANSKI, validBody(POLPRIL)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessCode", matchesPattern("\\d{4}")))
                    .andExpect(jsonPath("$.eRxKey", matchesPattern("[A-Z0-9]{44}")));
        }
        assertThat(jdbc.queryForObject("select count(distinct erx_key) from prescription", Integer.class))
                .isEqualTo(13);
    }

    @Test
    void issueValidatesRequest() throws Exception {
        LocalDate today = today();
        issue("doctor", SZYMANSKI, body(today, today.plusDays(5), "[]")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("items"));
        issue("doctor", SZYMANSKI, "{\"validFrom\":\"" + today + "\",\"validUntil\":\"" + today
                + "\",\"kind\":\"hospital_order\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
        issue("doctor", SZYMANSKI, body(today, today.minusDays(1), items(POLPRIL, "oral", "30%", "1", "30", "1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("validUntil"));
        issue("doctor", SZYMANSKI, body(today, today.plusDays(1), items(UUID.randomUUID().toString(), "oral", "30%",
                "1", "30", "1"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].drugId"))
                .andExpect(jsonPath("$.errors[0].code").value("notFound"));
        issue("doctor", SZYMANSKI, body(today, today.plusDays(1), items(POLPRIL, "oral", "30%", "0", "30", "1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].dosage.dose"));
        issue("doctor", SZYMANSKI, body(today, today.plusDays(1), items(POLPRIL, "oral", "30%", "-1", "30", "1")))
                .andExpect(status().isUnprocessableContent());
        issue("doctor", SZYMANSKI, body(today, today.plusDays(1), items(POLPRIL, "oral", "30%", "1.2345", "30", "1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].dosage.dose"));
        issue("doctor", SZYMANSKI, body(today, today.plusDays(1), items(POLPRIL, "oral", "30%", "1", "0", "1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].dosage.durationDays"));
        issue("doctor", SZYMANSKI, body(today, today.plusDays(1), items(POLPRIL, "oral", "30%", "1", "30", "0")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantityPackages"));
        // droga i refundacja musza byc dozwolone dla leku z katalogu
        issue("doctor", SZYMANSKI, body(today, today.plusDays(1), items(POLPRIL, "iv", "30%", "1", "30", "1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].dosage.route"))
                .andExpect(jsonPath("$.errors[0].code").value("notAllowed"));
        issue("doctor", SZYMANSKI, body(today, today.plusDays(1), items(POLPRIL, "oral", "100%", "1", "30", "1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].reimbursement"));
        // nieznane wartosci enumow
        issue("doctor", SZYMANSKI, validBody(POLPRIL).replace("\"BID\"", "\"CO_DWA\""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].dosage.frequency"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("Q12H")));
        issue("doctor", SZYMANSKI, validBody(POLPRIL).replace("\"e_prescription\"", "\"inna\""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("kind"));
        issue("doctor", SZYMANSKI, validBody(POLPRIL).replace("\"BID\"", "\"BID\",\"timesOfDay\":[\"dawn\"]"))
                .andExpect(status().isUnprocessableContent());
        assertThat(count()).isEqualTo(8);
    }

    @Test
    void issueChecksPatientAndEncounter() throws Exception {
        issue("doctor", UUID.randomUUID().toString(), validBody(POLPRIL)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        issue("doctor", "to-nie-uuid", validBody(POLPRIL)).andExpect(status().isNotFound());
        LocalDate today = today();
        String items = items(POLPRIL, "oral", "30%", "1", "30", "1");
        issue("doctor", SZYMANSKI, "{\"patientId\":\"" + KOWALSKI + "\",\"validFrom\":\"" + today
                + "\",\"validUntil\":\"" + today + "\",\"kind\":\"hospital_order\",\"items\":" + items + "}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("patientId"));
        issue("doctor", KOWALSKI, "{\"encounterId\":\"" + WISNIEWSKA_ENCOUNTER + "\",\"validFrom\":\"" + today
                + "\",\"validUntil\":\"" + today + "\",\"kind\":\"hospital_order\",\"items\":" + items + "}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("encounterId"));
        assertThat(count()).isEqualTo(8);
    }

    @Test
    void issueAllowsRepeatedDrugsAndSubcutaneousDrugs() throws Exception {
        LocalDate today = today();
        String two = "[" + item(POLPRIL, "oral", "30%", "5", "30", "1") + "," + item(POLPRIL, "oral", "none", "5", "10",
                "1") + "," + item(CLEXANE, "sc", "100%", "40", "10", "1") + "]";
        issue("doctor", SZYMANSKI, body(today, today.plusDays(30), two)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(3)));
    }

    // --- anulowanie ---

    @Test
    void cancelStoresReasonActorAndPublishesEvent() throws Exception {
        cancel("doctor", RX_KOWALSKI, "{\"reason\":\"  Błąd dawkowania  \",\"status\":\"issued\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.cancelReason").value("Błąd dawkowania"))
                .andExpect(jsonPath("$.cancelledAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.updatedById").value(DOCTOR_STAFF))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.accessCode").value("4821"));
        as("pharmacist", get("/api/v1/prescriptions/{id}", RX_KOWALSKI))
                .andExpect(jsonPath("$.status").value("cancelled"));
        list("status=cancelled").andExpect(jsonPath("$.items[*].id", contains(RX_KOWALSKI)));
        as("doctor", get("/api/v1/patients/{id}/active-medications", KOWALSKI)).andExpect(jsonPath("$", hasSize(0)));
        as("doctor", get("/api/v1/patients/{id}/ehr-summary", KOWALSKI))
                .andExpect(jsonPath("$.activeMedications", hasSize(0)));
        assertThat(events.stream(PrescriptionCancelled.class)).singleElement().satisfies(e -> {
            assertThat(e.prescriptionId().toString()).isEqualTo(RX_KOWALSKI);
            assertThat(e.actorId().toString()).isEqualTo(DOCTOR_STAFF);
            assertThat(e.reason()).isEqualTo("Błąd dawkowania");
        });
    }

    @Test
    void cancelWorksWithoutBodyOrReasonAndFromPartiallyDispensed() throws Exception {
        as("doctor", post("/api/v1/prescriptions/{id}/cancel", RX_XARELTO)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.cancelReason").doesNotExist())
                .andExpect(jsonPath("$.cancelledAt").exists());
        cancel("doctor", RX_ASPIRIN, "{\"reason\":\"   \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelReason").doesNotExist());
        jdbc.update("update prescription set status = 'partially_dispensed' where id = ?::uuid", RX_PANTOPRAZOL);
        cancel("doctor", RX_PANTOPRAZOL, "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    @ParameterizedTest
    @ValueSource(strings = {RX_VENTOLIN, RX_XARELTO_DISPENSED, RX_KOWALSKI_EXPIRED})
    void cancelOfFinalMockPrescriptionIsConflict(String id) throws Exception {
        cancel("doctor", id, "{\"reason\":\"Za późno\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(events.stream(PrescriptionCancelled.class)).isEmpty();
    }

    @Test
    void doubleCancelIsConflict() throws Exception {
        cancel("doctor", RX_XARELTO, "{\"reason\":\"Raz\"}").andExpect(status().isOk());
        cancel("doctor", RX_XARELTO, "{\"reason\":\"Dwa\"}").andExpect(status().isConflict());
        as("doctor", get("/api/v1/prescriptions/{id}", RX_XARELTO)).andExpect(jsonPath("$.cancelReason").value("Raz"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dispensed", "cancelled", "expired"})
    void cancelOfPrescriptionWithFinalStoredStatusIsConflict(String finalStatus) throws Exception {
        // przed pierwszym odczytem encji w tej transakcji
        jdbc.update("update prescription set status = ? where id = ?::uuid", finalStatus, RX_PANTOPRAZOL);
        cancel("doctor", RX_PANTOPRAZOL, "{}").andExpect(status().isConflict());
        as("doctor", get("/api/v1/prescriptions/{id}", RX_PANTOPRAZOL))
                .andExpect(jsonPath("$.status").value(finalStatus));
    }

    @Test
    void cancelRespectsVersionAnd404() throws Exception {
        cancel("doctor", RX_XARELTO, "{\"version\":4}").andExpect(status().isConflict());
        as("doctor", get("/api/v1/prescriptions/{id}", RX_XARELTO)).andExpect(jsonPath("$.status").value("issued"));
        cancel("doctor", RX_XARELTO, "{\"version\":0,\"reason\":\"Ok\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        cancel("doctor", UUID.randomUUID().toString(), "{}").andExpect(status().isNotFound());
        cancel("doctor", "to-nie-uuid", "{}").andExpect(status().isNotFound());
    }

    // --- pomocnicze ---

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from prescription", Integer.class);
    }

    private ResultActions list(String query) throws Exception {
        return as("doctor", get("/api/v1/prescriptions?" + query));
    }

    private ResultActions issue(String login, String patientId, String body) throws Exception {
        return as(login, post("/api/v1/patients/{id}/prescriptions", patientId).contentType(JSON).content(body));
    }

    private ResultActions cancel(String login, String id, String body) throws Exception {
        return as(login, post("/api/v1/prescriptions/{id}/cancel", id).contentType(JSON).content(body));
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

    /** Poprawne zadanie: jedna pozycja (`BID`, doustnie, 30%), wazna dzis + 30 dni. */
    private static String validBody(String drugId) {
        LocalDate today = today();
        return body(today, today.plusDays(30), items(drugId, "oral", "30%", "5", "30", "1"));
    }

    private static String body(LocalDate from, LocalDate until, String items) {
        return "{\"validFrom\":\"" + from + "\",\"validUntil\":\"" + until + "\",\"kind\":\"e_prescription\",\"items\":"
                + items + "}";
    }

    private static String items(String drugId, String route, String reimbursement, String dose, String days,
            String quantity) {
        return "[" + item(drugId, route, reimbursement, dose, days, quantity) + "]";
    }

    private static String item(String drugId, String route, String reimbursement, String dose, String days,
            String quantity) {
        return "{\"drugId\":\"" + drugId + "\",\"dosage\":{\"dose\":" + dose + ",\"doseUnit\":\"mg\",\"route\":\""
                + route + "\",\"frequency\":\"BID\",\"durationDays\":" + days + ",\"asNeeded\":false},"
                + "\"quantityPackages\":" + quantity + ",\"reimbursement\":\"" + reimbursement
                + "\",\"substitutionAllowed\":true}";
    }
}
