package robert_neat.his_backend.staff;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.DemoAccounts;

/** Kontrakt JSON `/wards` i `/staff` na danych mock (6 oddzialow, 10 osob) oraz kontach demo (1 oddzial, 8 osob). */
@WithMockUser(authorities = {"staff:read", "ward:read"})
class StaffApiTest extends ApiIntegrationTest {

    private static final String KARDIOLOGIA = "36877e50-4f0b-5b6c-bc22-983228fe0d83";
    private static final String AMBULATORIUM = "9062e817-70be-578e-b07e-136085e8e294";
    private static final String ANNA_NOWAK = "16259545-f97c-531d-b9cd-6ba115379372";

    @Autowired
    private MockMvc mvc;

    @Test
    void wardsReturnsMockWardsAndDemoWardInContractShape() throws Exception {
        mvc.perform(get("/api/v1/wards"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(6 + DemoAccounts.WARDS)))
                // sort wg nazwy: oddzial kont demo ("Administracja...") jest pierwszy, potem Chirurgia Ogolna
                .andExpect(jsonPath("$[0].id").value(DemoAccounts.WARD_ID))
                .andExpect(jsonPath("$[0].shortName").value("DEMO"))
                .andExpect(jsonPath("$[1].id").value("b0225b77-deaa-5d03-b73e-931004b89234"))
                .andExpect(jsonPath("$[1].name").value("Chirurgia Ogólna"))
                .andExpect(jsonPath("$[1].shortName").value("CHG"))
                .andExpect(jsonPath("$[1].floor").value("1"))
                .andExpect(jsonPath("$[1].beds").value(26))
                .andExpect(jsonPath("$[?(@.shortName=='AMB')].beds").value(0));
    }

    @Test
    void staffReturnsMockAndDemoMembersWithContractFields() throws Exception {
        mvc.perform(get("/api/v1/staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10 + DemoAccounts.STAFF)))
                // sort: lastName, firstName ("Admin" konta demo jest pierwszy)
                .andExpect(jsonPath("$[0].lastName").value("Admin"))
                .andExpect(jsonPath("$[?(@.employeeId=='admin')].role").value("admin"))
                .andExpect(jsonPath("$[?(@.id=='" + ANNA_NOWAK + "')].title").value("lek."))
                .andExpect(jsonPath("$[?(@.id=='" + ANNA_NOWAK + "')].firstName").value("Anna"))
                .andExpect(jsonPath("$[?(@.id=='" + ANNA_NOWAK + "')].role").value("doctor"))
                .andExpect(jsonPath("$[?(@.id=='" + ANNA_NOWAK + "')].specialization").value("Choroby wewnętrzne"))
                .andExpect(jsonPath("$[?(@.id=='" + ANNA_NOWAK + "')].wardId")
                        .value("25c25490-5067-5aaa-bcf5-5dc23f56588b"))
                .andExpect(jsonPath("$[?(@.id=='" + ANNA_NOWAK + "')].phone").value("+48 601 100 001"))
                .andExpect(jsonPath("$[?(@.id=='" + ANNA_NOWAK + "')].pwz").value("2001117"))
                .andExpect(jsonPath("$[?(@.id=='" + ANNA_NOWAK + "')].employeeId").value("EMP-0001"))
                .andExpect(jsonPath("$[*].accountStatus", everyItem(is("active"))))
                .andExpect(jsonPath("$[*].online", everyItem(is(false))))
                .andExpect(jsonPath("$[?(@.wardId=='" + DemoAccounts.WARD_ID + "')]", hasSize(DemoAccounts.STAFF)));
    }

    @Test
    void absentOptionalFieldsAreOmittedNotNull() throws Exception {
        // email jest NULL w danych mock; specialization brak u pielegniarek
        mvc.perform(get("/api/v1/staff/{id}", ANNA_NOWAK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("null"))));
        mvc.perform(get("/api/v1/staff").param("role", "nurse"))
                .andExpect(jsonPath("$[?(@.lastName=='Zielińska')].specialization").isEmpty());
    }

    @Test
    void staffFilteredByRole() throws Exception {
        mvc.perform(get("/api/v1/staff").param("role", "doctor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5 + 2))) // 5 z mock + `user` i `doctor` z kont demo
                .andExpect(jsonPath("$[*].role", everyItem(is("doctor"))));
        mvc.perform(get("/api/v1/staff").param("role", "pharmacist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeeId").value("pharmacist"));
    }

    @Test
    void staffFilteredByWardAndRole() throws Exception {
        mvc.perform(get("/api/v1/staff").param("wardId", KARDIOLOGIA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].wardId", everyItem(is(KARDIOLOGIA))));
        mvc.perform(get("/api/v1/staff").param("wardId", KARDIOLOGIA).param("role", "nurse"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].lastName").value("Wójcik"));
        mvc.perform(get("/api/v1/staff").param("wardId", AMBULATORIUM))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/staff").param("wardId", DemoAccounts.WARD_ID).param("role", "nurse"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeeId").value("nurse"));
    }

    @Test
    void staffById() throws Exception {
        mvc.perform(get("/api/v1/staff/{id}", ANNA_NOWAK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ANNA_NOWAK))
                .andExpect(jsonPath("$.lastName").value("Nowak"))
                .andExpect(jsonPath("$.role").value("doctor"))
                .andExpect(jsonPath("$.accountStatus").value("active"))
                .andExpect(jsonPath("$.online").value(false));
    }

    @Test
    void unknownStaffIdIs404Problem() throws Exception {
        mvc.perform(get("/api/v1/staff/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.type").exists());
    }

    @Test
    void malformedStaffIdIs404() throws Exception {
        mvc.perform(get("/api/v1/staff/{id}", "pat-001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unknownRoleIs422WithFieldError() throws Exception {
        mvc.perform(get("/api/v1/staff").param("role", "bogus"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("role"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void malformedWardIdIs422() throws Exception {
        mvc.perform(get("/api/v1/staff").param("wardId", "oddzial-1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("wardId"));
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/wards").with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
    }
}
