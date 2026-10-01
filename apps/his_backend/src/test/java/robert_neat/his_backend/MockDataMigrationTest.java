package robert_neat.his_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import liquibase.integration.spring.SpringLiquibase;

/**
 * Kontekst Liquibase `reference,mock`: dane slownikowe + dane demonstracyjne wygenerowane z mockow
 * frontendu. Sam start kontekstu dowodzi, ze wszystkie CHECK-i, FK i indeksy unikalne przyjely dane.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.liquibase.contexts=reference,mock")
class MockDataMigrationTest {

    private static final String CHANGELOG = "classpath:db/changelog/db.changelog-master.yaml";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void rowCountsMatchManifest() {
        MockManifest manifest = MockManifest.load();
        assertThat(manifest.mock()).isNotEmpty();
        manifest.reference().forEach(this::assertCount);
        manifest.mock().forEach((table, expected) ->
                assertCount(table, expected + DemoAccounts.EXTRA_ROWS.getOrDefault(table, 0)));
    }

    @Test
    void everyTableIsCoveredByManifest() {
        MockManifest manifest = MockManifest.load();
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND table_name NOT LIKE 'databasechangelog%'",
                String.class);
        assertThat(tables).hasSize(49);
        for (String table : tables) {
            assertThat(manifest.reference().containsKey(table) || manifest.mock().containsKey(table))
                    .as("tabela %s w manifescie", table).isTrue();
        }
    }

    @Test
    void allConstraintsAreValidated() {
        Integer invalid = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE NOT convalidated", Integer.class);
        assertThat(invalid).isZero();
    }

    @Test
    void admissionsAreConsistentWithPatientStatus() {
        Integer admittedPatients = jdbc.queryForObject(
                "SELECT count(*) FROM patient WHERE status = 'admitted'", Integer.class);
        Integer activeAdmissions = jdbc.queryForObject(
                "SELECT count(*) FROM admission WHERE status = 'active'", Integer.class);
        assertThat(activeAdmissions).isEqualTo(admittedPatients);
        Integer mismatched = jdbc.queryForObject("""
                SELECT count(*) FROM admission a JOIN patient p ON p.id = a.patient_id
                WHERE (a.status = 'active') <> (p.status = 'admitted')
                   OR (a.status = 'discharged' AND p.status <> 'discharged')
                """, Integer.class);
        assertThat(mismatched).isZero();
        Integer linkedNotes = jdbc.queryForObject(
                "SELECT count(*) FROM admission WHERE discharge_summary_note_id IS NOT NULL", Integer.class);
        assertThat(linkedNotes).isEqualTo(1);
    }

    @Test
    void mrnSequenceContinuesAfterMockPatients() {
        Long maxMrn = jdbc.queryForObject(
                "SELECT max(substring(mrn from '[0-9]+$')::bigint) FROM patient", Long.class);
        Long next = jdbc.queryForObject("SELECT nextval('patient_mrn_seq')", Long.class);
        assertThat(next).isEqualTo(maxMrn + 1);
    }

    @Test
    void demoAccountsUseSharedDemoPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        List<String> hashes = jdbc.queryForList(
                "SELECT password_hash FROM user_account WHERE employee_id LIKE 'EMP-%'", String.class);
        assertThat(hashes).isNotEmpty().allSatisfy(h -> assertThat(encoder.matches("HisDemo2026!", h)).isTrue());
    }

    @Test
    void demoAccountsCoexistWithMockStaff() {
        Integer accounts = jdbc.queryForObject("SELECT count(*) FROM user_account", Integer.class);
        assertThat(accounts).isEqualTo(MockManifest.load().mock().get("user_account") + DemoAccounts.ACCOUNTS);
    }

    @Test
    void timestampsAreRelativeToMigrationTime() {
        Integer fresh = jdbc.queryForObject(
                "SELECT count(*) FROM vital_signs WHERE recorded_at > now() - interval '6 hours'", Integer.class);
        assertThat(fresh).isPositive();
        Integer futureSlots = jdbc.queryForObject(
                "SELECT count(*) FROM schedule_slot WHERE start_at > now()", Integer.class);
        Integer pastSlots = jdbc.queryForObject(
                "SELECT count(*) FROM schedule_slot WHERE start_at < now() - interval '1 day'", Integer.class);
        assertThat(futureSlots).isPositive();
        assertThat(pastSlots).isPositive();
    }

    @Test
    void rerunningMigrationsKeepsChecksumsAndData() throws Exception {
        List<Map<String, Object>> before = changelogState();
        Integer patientsBefore = jdbc.queryForObject("SELECT count(*) FROM patient", Integer.class);

        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.setContexts("reference,mock");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();

        assertThat(changelogState()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient", Integer.class)).isEqualTo(patientsBefore);
    }

    private List<Map<String, Object>> changelogState() {
        return jdbc.queryForList(
                "SELECT id, author, filename, md5sum, exectype FROM databasechangelog ORDER BY orderexecuted");
    }

    private void assertCount(String table, Integer expected) {
        Integer actual = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        assertThat(actual).as("liczba wierszy w %s", table).isEqualTo(expected);
    }
}
