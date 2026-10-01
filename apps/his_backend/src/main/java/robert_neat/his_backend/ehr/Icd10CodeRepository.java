package robert_neat.his_backend.ehr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface Icd10CodeRepository extends JpaRepository<Icd10Code, String>, JpaSpecificationExecutor<Icd10Code> {
}
