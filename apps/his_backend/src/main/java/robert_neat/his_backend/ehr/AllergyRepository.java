package robert_neat.his_backend.ehr;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AllergyRepository extends JpaRepository<Allergy, UUID> {

    List<Allergy> findByPatientIdOrderByRecordedAtDescIdAsc(UUID patientId);

    Optional<Allergy> findByIdAndPatientId(UUID id, UUID patientId);
}
