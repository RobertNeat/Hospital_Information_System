package robert_neat.his_backend.alert;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Klucz zlozony `alert_acknowledgement` (alert + pracownik). */
@Embeddable
public record AlertAcknowledgementId(
        @Column(name = "alert_id", nullable = false, updatable = false) UUID alertId,
        @Column(name = "staff_id", nullable = false, updatable = false) UUID staffId) implements Serializable {
}
