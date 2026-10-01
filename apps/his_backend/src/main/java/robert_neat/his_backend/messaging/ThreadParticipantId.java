package robert_neat.his_backend.messaging;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Klucz zlozony `thread_participant` (watek + pracownik). */
@Embeddable
public record ThreadParticipantId(
        @Column(name = "thread_id", nullable = false, updatable = false) UUID threadId,
        @Column(name = "staff_id", nullable = false, updatable = false) UUID staffId) implements Serializable {
}
