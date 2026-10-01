package robert_neat.his_backend.catalog;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LabPanelRepository extends JpaRepository<LabPanel, UUID> {
}
