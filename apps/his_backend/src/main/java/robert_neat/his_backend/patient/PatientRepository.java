package robert_neat.his_backend.patient;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PatientRepository extends JpaRepository<Patient, UUID>, JpaSpecificationExecutor<Patient> {

    Optional<Patient> findByPesel(String pesel);

    boolean existsByPesel(String pesel);

    boolean existsByPeselAndIdNot(String pesel, UUID id);
}
