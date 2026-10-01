package robert_neat.his_backend.patient;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentEpisodeRepository extends JpaRepository<TreatmentEpisode, UUID> {

    /** Epizody pacjenta od najnowszego. */
    List<TreatmentEpisode> findByPatientIdOrderByStartAtDescIdAsc(UUID patientId);
}
