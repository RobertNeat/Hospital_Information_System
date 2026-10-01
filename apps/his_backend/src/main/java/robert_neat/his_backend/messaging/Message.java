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

/** Wiadomosc w watku (`message`) - niezmienna; `sentAt`/`senderId` pelnia role audytu (bez `AuditableEntity`). */
@Entity
@Table(name = "message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "thread_id", nullable = false, updatable = false)
    private UUID threadId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "body", nullable = false, updatable = false, columnDefinition = "text")
    private String body;

    @Column(name = "priority", nullable = false, updatable = false, length = 10)
    private Priority priority;

    static Message send(UUID threadId, UUID senderId, Instant sentAt, String body, Priority priority) {
        Message m = new Message();
        m.threadId = threadId;
        m.senderId = senderId;
        m.sentAt = sentAt;
        m.body = body;
        m.priority = priority;
        return m;
    }
}
