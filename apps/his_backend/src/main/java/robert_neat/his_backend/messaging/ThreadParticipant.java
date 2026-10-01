package robert_neat.his_backend.messaging;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Uczestnik watku (`thread_participant`) + jego kursor odczytu: wiadomosci wyslane do `lastReadAt` wlacznie sa dla
 * niego przeczytane. Zrodlo `unreadCount` i `readByIds`.
 */
@Entity
@Table(name = "thread_participant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThreadParticipant {

    @EmbeddedId
    private ThreadParticipantId id;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    static ThreadParticipant join(UUID threadId, UUID staffId, Instant joinedAt, Instant lastReadAt) {
        ThreadParticipant p = new ThreadParticipant();
        p.id = new ThreadParticipantId(threadId, staffId);
        p.joinedAt = joinedAt;
        p.lastReadAt = lastReadAt;
        return p;
    }

    public UUID threadId() {
        return id.threadId();
    }

    public UUID staffId() {
        return id.staffId();
    }

    /** Przesuwa kursor do przodu (nigdy wstecz). */
    void markReadUpTo(Instant at) {
        if (lastReadAt == null || at.isAfter(lastReadAt)) {
            this.lastReadAt = at;
        }
    }
}
