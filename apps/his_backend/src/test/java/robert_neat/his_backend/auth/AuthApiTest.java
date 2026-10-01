package robert_neat.his_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.DemoAccounts;
import robert_neat.his_backend.security.RolePermissions;

/** Logowanie, token JWT, rejestracja, aktywacja/blokada oraz ksztalt bledow 401/403 (application/problem+json). */
class AuthApiTest extends ApiIntegrationTest {

    private static final String PL_JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private JwtDecoder jwtDecoder;
    @PersistenceContext
    private EntityManager em;

    // --- login ---

    @Test
    void adminLoginReturnsTokenUserAndPermissionsThenMeWorks() throws Exception {
        String body = login("admin", "admin").andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("admin"))
                .andExpect(jsonPath("$.user.employeeId").value("admin"))
                .andExpect(jsonPath("$.user.accountStatus").value("active"))
                .andExpect(jsonPath("$.user.permissions", hasItem("account:manage")))
                .andExpect(jsonPath("$.user.permissions", not(hasItem("lab-order:create"))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("admin"))
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.permissions", hasItem("account:manage")));
    }

    @Test
    void userAccountIsDoctor() throws Exception {
        login("user", "user").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("doctor"))
                .andExpect(jsonPath("$.user.permissions", hasItem("lab-order:create")))
                .andExpect(jsonPath("$.user.permissions", not(hasItem("account:manage"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "user", "doctor", "nurse", "lab-tech", "radiologist", "pharmacist", "registrar"})
    void everyDemoAccountLogsInWithItsRole(String login) throws Exception {
        login(login, login).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value(DemoAccounts.ROLE_BY_LOGIN.get(login)));
    }

    @Test
    void tokenCarriesContractClaims() throws Exception {
        String token = tokenOf("doctor");
        var jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(
                jdbc.queryForObject("SELECT id FROM user_account WHERE employee_id = 'doctor'", UUID.class).toString());
        assertThat(jwt.getClaimAsString("staffId")).isEqualTo(
                jdbc.queryForObject("SELECT staff_id FROM user_account WHERE employee_id = 'doctor'", UUID.class)
                        .toString());
        assertThat(jwt.getClaimAsString("employeeId")).isEqualTo("doctor");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("doctor");
        assertThat(jwt.getClaimAsString("wardId")).isEqualTo(DemoAccounts.WARD_ID);
        assertThat(jwt.getClaimAsStringList("authorities")).contains("ROLE_DOCTOR", "lab-order:create");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("his-backend");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now().plusSeconds(7 * 3600 + 3000));
    }

    @Test
    void successfulLoginUpdatesAccountState() throws Exception {
        jdbc.update("UPDATE user_account SET failed_attempts = 3 WHERE employee_id = 'nurse'");
        sync();
        login("nurse", "nurse").andExpect(status().isOk());
        sync();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT failed_attempts, locked_until, last_login_at FROM user_account WHERE employee_id = 'nurse'");
        assertThat(row.get("failed_attempts")).isEqualTo(0);
        assertThat(row.get("locked_until")).isNull();
        assertThat(row.get("last_login_at")).isNotNull();
    }

    @Test
    void wrongPasswordAndUnknownLoginGiveIdentical401AndCountFailures() throws Exception {
        String wrong = login("registrar", "zle-haslo").andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String unknown = login("nie-ma-takiego", "zle-haslo").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<String>read(wrong, "$.detail")).isEqualTo(JsonPath.<String>read(unknown, "$.detail"));
        assertThat(failedAttempts("registrar")).isEqualTo(1);
        login("registrar", "znowu-zle").andExpect(status().isUnauthorized());
        assertThat(failedAttempts("registrar")).isEqualTo(2);
    }

    @Test
    void blankLoginRequestIs422() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(PL_JSON).content("{\"employeeId\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void staleBearerHeaderDoesNotBlockPublicLogin() throws Exception {
        mvc.perform(post("/api/v1/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer to.nie.jest.token")
                        .contentType(PL_JSON).content(json("admin", "admin")))
                .andExpect(status().isOk());
    }

    // --- blokada konta ---

    @Test
    void fiveFailedAttemptsLockAccountThenLoginIs403() throws Exception {
        registerAndActivate("locktest-1");
        for (int i = 0; i < 4; i++) {
            login("locktest-1", "zle-haslo-" + i).andExpect(status().isUnauthorized());
        }
        assertThat(failedAttempts("locktest-1")).isEqualTo(4);
        login("locktest-1", "zle-haslo-5").andExpect(status().isUnauthorized());
        sync();
        assertThat(jdbc.queryForObject("SELECT locked_until FROM user_account WHERE employee_id = 'locktest-1'",
                java.sql.Timestamp.class)).isAfter(java.sql.Timestamp.from(Instant.now().plusSeconds(14 * 60)));

        // blokada czasowa obowiazuje nawet przy poprawnym hasle
        login("locktest-1", "Haslo-test-1").andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // po wygasnieciu blokady logowanie znowu dziala i zeruje stan
        jdbc.update("UPDATE user_account SET locked_until = now() - interval '1 minute' "
                + "WHERE employee_id = 'locktest-1'");
        sync();
        login("locktest-1", "Haslo-test-1").andExpect(status().isOk());
        assertThat(failedAttempts("locktest-1")).isZero();
    }

    // --- rejestracja i aktywacja ---

    @Test
    void registrationCreatesPendingAccountActivatedByAdmin() throws Exception {
        String staffId = register("nowy-1", "nowy1@szpital.test", "1234567")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountStatus").value("pending"))
                .andExpect(jsonPath("$.staffId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(staffId, "$.staffId");

        sync();
        assertThat(jdbc.queryForObject("SELECT account_status FROM user_account WHERE employee_id = 'nowy-1'",
                String.class)).isEqualTo("pending");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM user_account WHERE employee_id = 'nowy-1'",
                String.class)).startsWith("$2").isNotEqualTo("Haslo-test-1");

        // pending: poprawne haslo -> 403, zle haslo -> nadal 401 (bez ujawniania statusu)
        login("nowy-1", "Haslo-test-1").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        login("nowy-1", "zle").andExpect(status().isUnauthorized());

        String admin = tokenOf("admin");
        mvc.perform(post("/api/v1/staff/{id}/activate", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("active"));

        login("nowy-1", "Haslo-test-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("nurse"))
                .andExpect(jsonPath("$.user.id").value(id));

        mvc.perform(post("/api/v1/staff/{id}/lock", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("locked"));
        login("nowy-1", "Haslo-test-1").andExpect(status().isForbidden());
    }

    @Test
    void registrationRejectsDuplicatesWith409() throws Exception {
        register("dup-1", "dup@szpital.test", "7654321").andExpect(status().isCreated());
        register("dup-1", "inny@szpital.test", "7654322").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        register("dup-2", "DUP@szpital.test", "7654323").andExpect(status().isConflict());
        register("dup-3", "trzeci@szpital.test", "7654321").andExpect(status().isConflict());
        // identyfikator kolidujacy z kontem demo
        register("admin", "x@szpital.test", "7654324").andExpect(status().isConflict());
    }

    @Test
    void registrationValidatesInput() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(PL_JSON).content("""
                        {"firstName":"","lastName":"X","role":"nurse","title":"piel.","wardId":"%s",
                         "employeeId":"ab","password":"krotkie"}""".formatted(DemoAccounts.WARD_ID)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("employeeId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("firstName")));
        mvc.perform(post("/api/v1/auth/register").contentType(PL_JSON).content("""
                        {"firstName":"A","lastName":"B","role":"nurse","title":"piel.",
                         "wardId":"00000000-0000-0000-0000-000000000000",
                         "employeeId":"valid-1","password":"Haslo-test-1"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("wardId"));
    }

    @Test
    void onlyAdminCanActivateAndLock() throws Exception {
        String id = jdbc.queryForObject("SELECT staff_id FROM user_account WHERE employee_id = 'nurse'", String.class);
        String nurse = tokenOf("nurse");
        mvc.perform(post("/api/v1/staff/{id}/activate", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + nurse))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/staff/{id}/lock", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + nurse))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/staff/{id}/lock", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotLockOwnAccountAndUnknownStaffIs404() throws Exception {
        String admin = tokenOf("admin");
        String adminId = jdbc.queryForObject("SELECT staff_id FROM user_account WHERE employee_id = 'admin'",
                String.class);
        mvc.perform(post("/api/v1/staff/{id}/lock", adminId).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/staff/{id}/activate", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    // --- uprawnienia ---

    @Test
    void roleWithoutPermissionGets403ProblemOnProtectedResource() throws Exception {
        // `staff:read` maja wszystkie role z macierzy; usuwamy je z tokenu, by sprawdzic @PreAuthorize
        String token = tokenWithAuthorities(List.of("ROLE_NURSE", "message:read"));
        mvc.perform(get("/api/v1/staff").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/wards").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/wards").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOf("nurse")))
                .andExpect(status().isOk());
    }

    @Test
    void permissionMatrixFollowsContract() {
        assertThat(RolePermissions.permissionsOf(robert_neat.his_backend.staff.StaffRole.DOCTOR))
                .contains("lab-order:create", "prescription:create", "admission:discharge", "staff:read")
                .doesNotContain("account:manage", "lab-order:update-status");
        assertThat(RolePermissions.permissionsOf(robert_neat.his_backend.staff.StaffRole.REGISTRAR))
                .contains("patient:write", "admission:admit")
                .doesNotContain("ehr:read", "admission:discharge", "lab-order:create");
        assertThat(RolePermissions.authoritiesOf(robert_neat.his_backend.staff.StaffRole.ADMIN))
                .contains("ROLE_ADMIN", "account:manage", "vital-threshold:write");
    }

    // --- 401 ---

    @Test
    void missingOrMalformedOrInvalidTokensGet401Problem() throws Exception {
        expectUnauthorized(get("/api/v1/auth/me"));
        expectUnauthorized(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer smieci"));
        expectUnauthorized(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Basic YWRtaW46YWRtaW4="));

        Instant now = Instant.now();
        expectUnauthorized(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + encode(jwtEncoder, now.minusSeconds(7200), now.minusSeconds(3600), "his-backend")));
        expectUnauthorized(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + encode(
                otherKeyEncoder(), now, now.plusSeconds(3600), "his-backend")));
        expectUnauthorized(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + encode(
                jwtEncoder, now, now.plusSeconds(3600), "obcy-wystawca")));
    }

    @Test
    void publicAndClosedEndpoints() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        // reszta zamknieta: anonim 401, uwierzytelniony 403
        expectUnauthorized(get("/actuator/env"));
        expectUnauthorized(get("/cokolwiek"));
        mvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOf("admin")))
                .andExpect(status().isForbidden());
        expectUnauthorized(get("/api/v1/wards"));
    }

    @Test
    void logoutIs204ForAuthenticatedAndStatelessOtherwise() throws Exception {
        String token = tokenOf("admin");
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        // bezstanowo: serwer nie uniewaznia tokenu, wygasa sam
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        expectUnauthorized(post("/api/v1/auth/logout"));
    }

    // --- pomocnicze ---

    /** Test i aplikacja dziela jedna transakcje: przed odczytem JDBC zapisz zmiany JPA, po zapisie JDBC odswiez JPA. */
    private void sync() {
        em.flush();
        em.clear();
    }

    private ResultActions login(String employeeId, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(PL_JSON).content(json(employeeId, password)));
    }

    private static String json(String employeeId, String password) {
        return "{\"employeeId\":\"" + employeeId + "\",\"password\":\"" + password + "\"}";
    }

    private String tokenOf(String login) throws Exception {
        String body = login(login, login).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions register(String employeeId, String email, String pwz) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(PL_JSON).content("""
                {"firstName":"Jan","lastName":"Testowy","role":"nurse","title":"piel.","specialization":"",
                 "pwz":"%s","wardId":"%s","phone":"+48 600 000 000","email":"%s",
                 "employeeId":"%s","password":"Haslo-test-1"}""".formatted(pwz, DemoAccounts.WARD_ID, email,
                employeeId)));
    }

    /** Konto utworzone przez rejestracje i aktywowane bezposrednio w bazie (test wlasny, rollback po tescie). */
    private void registerAndActivate(String employeeId) throws Exception {
        register(employeeId, employeeId + "@szpital.test", "1111111").andExpect(status().isCreated());
        sync();
        jdbc.update("UPDATE user_account SET account_status = 'active' WHERE employee_id = ?", employeeId);
        sync();
    }

    private int failedAttempts(String employeeId) {
        sync();
        return jdbc.queryForObject("SELECT failed_attempts FROM user_account WHERE employee_id = ?", Integer.class,
                employeeId);
    }

    private void expectUnauthorized(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.type").exists());
    }

    private String tokenWithAuthorities(List<String> authorities) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("his-backend")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(3600))
                .claim("staffId", UUID.randomUUID().toString())
                .claim("employeeId", "bez-uprawnien").claim("role", "nurse")
                .claim("wardId", DemoAccounts.WARD_ID).claim("authorities", authorities).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String encode(JwtEncoder encoder, Instant issuedAt, Instant expiresAt, String issuer) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(UUID.randomUUID().toString())
                .issuedAt(issuedAt).expiresAt(expiresAt)
                .claim("staffId", UUID.randomUUID().toString())
                .claim("employeeId", "admin").claim("role", "admin")
                .claim("wardId", DemoAccounts.WARD_ID).claim("authorities", List.of("ROLE_ADMIN")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static JwtEncoder otherKeyEncoder() {
        byte[] key = "inny-klucz-0123456789-abcdefghijklmnopqrstuv".getBytes(StandardCharsets.UTF_8);
        return new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(key, "HmacSHA256")));
    }

    @Test
    void bcryptHashesFromMigrationAreBareNotDelegating() {
        String hash = jdbc.queryForObject("SELECT password_hash FROM user_account WHERE employee_id = 'admin'",
                String.class);
        assertThat(hash).startsWith("$2a$10$");
        assertThat(new BCryptPasswordEncoder().matches("admin", hash)).isTrue();
    }

    @Test
    void registrationWardsArePublicAndMinimal() throws Exception {
        mvc.perform(get("/api/v1/auth/register/wards").header(HttpHeaders.AUTHORIZATION, "Bearer to.nie.jest.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].beds").doesNotExist())
                .andExpect(jsonPath("$[0].floor").doesNotExist());
        mvc.perform(post("/api/v1/auth/register/wards")).andExpect(status().isUnauthorized());
    }
}
