package robert_neat.his_backend.messaging;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Watek wiadomosci (`message_thread`). Uczestnicy to osobna tabela ({@link ThreadParticipant}); tabela nie ma
 * `updated_*` ani `version` (wiec bez {@code AuditableEntity}). `lastMessageAt` aktualizuje {@link #touch}.
 */
@Entity
@Table(name = "message_thread")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageThread {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "patient_id", updatable = false)
    private UUID patientId;

    @Column(name = "created_by_id", updatable = false)
    private UUID createdById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    static MessageThread start(String subject, UUID patientId, UUID createdById, Instant at) {
        MessageThread thread = new MessageThread();
        thread.subject = subject;
        thread.patientId = patientId;
        thread.createdById = createdById;
        thread.createdAt = at;
        thread.lastMessageAt = at;
        return thread;
    }

    /** Nowa wiadomosc przesuwa `lastMessageAt` (nigdy wstecz). */
    void touch(Instant sentAt) {
        if (sentAt.isAfter(lastMessageAt)) {
            this.lastMessageAt = sentAt;
        }
    }
}
