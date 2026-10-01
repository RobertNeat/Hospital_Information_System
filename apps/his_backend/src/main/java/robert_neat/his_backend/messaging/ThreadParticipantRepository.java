package robert_neat.his_backend.messaging;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ThreadParticipantRepository extends JpaRepository<ThreadParticipant, ThreadParticipantId> {

    List<ThreadParticipant> findByIdThreadIdIn(Collection<UUID> threadIds);
}
