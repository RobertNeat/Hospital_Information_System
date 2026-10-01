package robert_neat.his_backend.ehr;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContraindicationRepository extends JpaRepository<Contraindication, UUID> {

    List<Contraindication> findByPatientIdOrderByRecordedAtDescIdAsc(UUID patientId);
}
