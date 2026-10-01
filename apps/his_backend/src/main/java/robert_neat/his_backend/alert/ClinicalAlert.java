package robert_neat.his_backend.alert;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Alert kliniczny (`clinical_alert`) - niezmienny, tworzony wylacznie przez serwer ({@link AlertService#raise}).
 * Stan potwierdzenia nie nalezy do alertu: jest per uzytkownik ({@link AlertAcknowledgement}).
 */
@Entity
@Table(name = "clinical_alert")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicalAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private AlertType type;

    @Column(name = "severity", nullable = false, updatable = false, length = 10)
    private AlertSeverity severity;

    @Column(name = "patient_id", updatable = false)
    private UUID patientId;

    @Column(name = "message", nullable = false, updatable = false, columnDefinition = "text")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Embedded
    private AlertTarget target;

    static ClinicalAlert raise(AlertType type, AlertSeverity severity, UUID patientId, String message,
            Instant createdAt, AlertTarget target) {
        ClinicalAlert a = new ClinicalAlert();
        a.type = type;
        a.severity = severity;
        a.patientId = patientId;
        a.message = message;
        a.createdAt = createdAt;
        a.target = target;
        return a;
    }
}
