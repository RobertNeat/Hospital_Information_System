package robert_neat.his_backend.messaging;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByThreadIdOrderBySentAtAscIdAsc(UUID threadId);

    /**
     * Strona kursorowa (malejaco po `sentAt`, `id` jako tie-breaker): najnowsza strona przy braku
     * `before`, kolejne po znaczniku czasu najstarszego elementu poprzedniej strony (`nextBefore`).
     * Pobiera {@code limit} wierszy - wolajacy pyta o `size + 1`, by wykryc istnienie kolejnej strony.
     */
    List<Message> findByThreadIdAndSentAtBeforeOrderBySentAtDescIdDesc(UUID threadId, Instant before, Limit limit);

    List<Message> findByThreadIdOrderBySentAtDescIdDesc(UUID threadId, Limit limit);

    /**
     * Nieprzeczytane przez `staffId` w podanych watkach: wiadomosci cudze, nowsze niz jego `last_read_at`
     * (brak kursora = wszystkie cudze). Watki bez nieprzeczytanych sa pominiete.
     */
    @Query("""
            select new robert_neat.his_backend.messaging.ThreadUnread(p.id.threadId, count(m))
            from ThreadParticipant p join Message m on m.threadId = p.id.threadId
            where p.id.staffId = :staffId
              and p.id.threadId in :threadIds
              and m.senderId <> :staffId
              and (p.lastReadAt is null or m.sentAt > p.lastReadAt)
            group by p.id.threadId
            """)
    List<ThreadUnread> countUnread(@Param("staffId") UUID staffId, @Param("threadIds") Collection<UUID> threadIds);
}
