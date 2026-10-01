package robert_neat.his_backend.alert;

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
 * Potwierdzenie alertu przez pracownika (`alert_acknowledgement`, PK alert + pracownik): alert potwierdzony przez
 * jedna osobe pozostaje aktywny dla pozostalych. Zapis wylacznie przez
 * {@link AlertAcknowledgementRepository#acknowledge} (idempotentny insert); encja sluzy do odczytu.
 */
@Entity
@Table(name = "alert_acknowledgement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertAcknowledgement {

    @EmbeddedId
    private AlertAcknowledgementId id;

    @Column(name = "acknowledged_at", nullable = false, updatable = false)
    private Instant acknowledgedAt;

    public UUID alertId() {
        return id.alertId();
    }

    public UUID staffId() {
        return id.staffId();
    }
}
