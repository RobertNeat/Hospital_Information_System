package robert_neat.his_backend.messaging;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TeamTaskRepository extends JpaRepository<TeamTask, UUID>, JpaSpecificationExecutor<TeamTask> {
}
