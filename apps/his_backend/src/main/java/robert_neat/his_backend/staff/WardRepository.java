package robert_neat.his_backend.staff;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WardRepository extends JpaRepository<Ward, UUID> {

    boolean existsByShortNameIgnoreCase(String shortName);

    boolean existsByShortNameIgnoreCaseAndIdNot(String shortName, UUID id);
}
