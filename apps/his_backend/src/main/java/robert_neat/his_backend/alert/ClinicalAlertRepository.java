package robert_neat.his_backend.alert;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClinicalAlertRepository
        extends JpaRepository<ClinicalAlert, UUID>, JpaSpecificationExecutor<ClinicalAlert> {
}
