package robert_neat.his_backend.ehr;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentRepository extends JpaRepository<Treatment, UUID> {

    List<Treatment> findByPatientIdOrderByStartAtDescIdAsc(UUID patientId);
}
