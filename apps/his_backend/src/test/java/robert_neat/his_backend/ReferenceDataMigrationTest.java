package robert_neat.his_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Kontekst Liquibase `reference` (bez `mock`): tylko dane slownikowe, brak danych demonstracyjnych. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.liquibase.contexts=reference")
class ReferenceDataMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loadsVitalThresholds() {
        List<String> types = jdbc.queryForList("SELECT type FROM vital_threshold ORDER BY type", String.class);
        assertThat(types).containsExactly("diastolic", "heartRate", "respiratoryRate", "spo2", "systolic",
                "temperature");
    }

    @Test
    void mockTablesStayEmptyExceptDemoAccounts() {
        for (String table : MockManifest.load().mock().keySet()) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
            assertThat(count).as("wiersze w %s bez kontekstu mock", table)
                    .isEqualTo(DemoAccounts.EXTRA_ROWS.getOrDefault(table, 0));
        }
    }

    @Test
    void demoAccountsAreLoadedWithoutMockContext() {
        List<String> logins = jdbc.queryForList("SELECT employee_id FROM user_account", String.class);
        assertThat(logins).containsExactlyInAnyOrderElementsOf(DemoAccounts.LOGINS);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        for (Map<String, Object> row : jdbc.queryForList("SELECT employee_id, password_hash, account_status FROM user_account")) {
            String login = (String) row.get("employee_id");
            assertThat(encoder.matches(login, (String) row.get("password_hash"))).as("haslo konta %s", login).isTrue();
            assertThat(row.get("account_status")).isEqualTo("active");
        }
    }

    @Test
    void noMockChangesetWasExecuted() {
        List<Map<String, Object>> mockRows = jdbc.queryForList(
                "SELECT id FROM databasechangelog WHERE author = 'his-mock'");
        assertThat(mockRows).isEmpty();
        Integer reference = jdbc.queryForObject(
                "SELECT count(*) FROM databasechangelog WHERE author = 'his-ref'", Integer.class);
        assertThat(reference).isEqualTo(1);
    }
}
