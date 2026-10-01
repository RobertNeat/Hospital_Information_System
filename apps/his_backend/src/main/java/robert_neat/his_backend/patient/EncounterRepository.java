package robert_neat.his_backend.patient;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EncounterRepository extends JpaRepository<Encounter, UUID> {

    /** Kontakty pacjenta od najnowszego. */
    List<Encounter> findByPatientIdOrderByStartAtDescIdAsc(UUID patientId);

    /** 5 najnowszych kontaktow pacjenta (`EhrSummary.recentEncounters`). */
    List<Encounter> findTop5ByPatientIdOrderByStartAtDescIdAsc(UUID patientId);

    boolean existsByIdAndPatientId(UUID id, UUID patientId);
}
