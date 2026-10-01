package robert_neat.his_backend.messaging;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageThreadRepository extends JpaRepository<MessageThread, UUID> {

    /** Watki, w ktorych `staffId` jest uczestnikiem (opcjonalnie tylko o danym pacjencie). */
    @Query("""
            select t from MessageThread t
            where t.id in (select p.id.threadId from ThreadParticipant p where p.id.staffId = :staffId)
              and (:patientId is null or t.patientId = :patientId)
            """)
    Page<MessageThread> findVisibleTo(@Param("staffId") UUID staffId, @Param("patientId") UUID patientId,
            Pageable pageable);
}
