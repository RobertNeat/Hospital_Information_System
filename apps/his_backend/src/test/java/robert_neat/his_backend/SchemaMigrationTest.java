package robert_neat.his_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaMigrationTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "ward", "staff_member", "user_account",
            "patient", "patient_flag", "treatment_episode", "encounter", "admission",
            "clinical_note", "clinical_note_symptom", "diagnosis", "episode_diagnosis",
            "allergy", "allergy_atc_code", "contraindication", "treatment",
            "lab_test", "lab_test_specimen", "lab_analyte_definition", "lab_panel", "lab_panel_test",
            "imaging_exam", "schedule_slot",
            "drug", "drug_route", "drug_reimbursement_option", "drug_interacts_with_atc",
            "vital_threshold",
            "lab_order", "lab_order_item", "lab_order_status_change", "lab_result", "lab_observation",
            "imaging_order", "imaging_order_status_change", "imaging_result",
            "prescription", "prescription_item", "prescription_item_time_of_day",
            "vital_signs",
            "message_thread", "thread_participant", "message", "clinical_alert",
            "alert_acknowledgement", "team_task", "handoff_note", "handoff_patient_note");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationCreatesAllTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);
        assertThat(tables).containsAll(EXPECTED_TABLES);
    }

    @Test
    void icd10CodeTableIsDropped() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'icd10_code'",
                String.class);
        assertThat(tables).isEmpty();
    }

    @Test
    void mrnSequenceExists() {
        Long first = jdbc.queryForObject("SELECT nextval('patient_mrn_seq')", Long.class);
        assertThat(first).isNotNull();
    }

    @Test
    void enumCheckRejectsInvalidValue() {
        jdbc.update("INSERT INTO ward (id, name, short_name, floor, beds) VALUES (?, 'X', 'X', '0', 1)",
                UUID.randomUUID());
        UUID ward = jdbc.queryForObject("SELECT id FROM ward WHERE short_name = 'X'", UUID.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO staff_member (id, title, first_name, last_name, role, ward_id) "
                        + "VALUES (?, 'lek.', 'A', 'B', 'wizard', ?)",
                UUID.randomUUID(), ward))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void partialUniqueIndexesAllowManyNullsButRejectDuplicates() {
        UUID ward = UUID.randomUUID();
        jdbc.update("INSERT INTO ward (id, name, short_name, floor, beds) VALUES (?, 'W', 'PU', '0', 1)", ward);
        // dwa wiersze bez PWZ / employee_id - dozwolone
        insertStaff(ward, null);
        insertStaff(ward, null);
        // duplikat PWZ - odrzucony
        insertStaff(ward, "1234567");
        assertThatThrownBy(() -> insertStaff(ward, "1234567"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneActiveAdmissionPerPatient() {
        UUID ward = UUID.randomUUID();
        jdbc.update("INSERT INTO ward (id, name, short_name, floor, beds) VALUES (?, 'W', 'AD', '0', 1)", ward);
        UUID doctor = insertStaff(ward, null);
        UUID patient = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO patient (id, mrn, pesel, first_name, last_name, birth_date, gender,
                    address_street, address_building_number, address_postal_code, address_city, address_country,
                    insurance_status, insurance_nfz_branch, insurance_payer)
                VALUES (?, 'HIS/2026/T1', '12345678901', 'A', 'B', DATE '1990-01-01', 'female',
                    's', '1', '00-001', 'c', 'PL', 'active', '01', 'NFZ')
                """, patient);
        insertAdmission(patient, ward, doctor, "active");
        insertAdmission(patient, ward, doctor, "discharged");
        assertThatThrownBy(() -> insertAdmission(patient, ward, doctor, "active"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // pacjent bez PESEL wymaga powodu
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO patient (id, mrn, first_name, last_name, birth_date, gender,
                    address_street, address_building_number, address_postal_code, address_city, address_country,
                    insurance_status, insurance_nfz_branch, insurance_payer)
                VALUES (?, 'HIS/2026/T2', 'A', 'B', DATE '1990-01-01', 'female',
                    's', '1', '00-001', 'c', 'PL', 'active', '01', 'NFZ')
                """, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertStaff(UUID ward, String pwz) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO staff_member (id, title, first_name, last_name, role, ward_id, pwz) "
                + "VALUES (?, 'lek.', 'A', 'B', 'doctor', ?, ?)", id, ward, pwz);
        return id;
    }

    private void insertAdmission(UUID patient, UUID ward, UUID doctor, String status) {
        jdbc.update("""
                INSERT INTO admission (id, patient_id, status, admission_type, admitted_at, ward_id,
                    attending_physician_id, reason, discharged_at)
                VALUES (?, ?, ?, 'planned', now() - interval '2 day', ?, ?, 'r',
                    CASE WHEN ? = 'discharged' THEN now() ELSE NULL END)
                """, UUID.randomUUID(), patient, status, ward, doctor, status);
    }
}
