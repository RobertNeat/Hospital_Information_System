package robert_neat.his_backend.imaging;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import robert_neat.his_backend.catalog.ImagingModality;

/**
 * Wynik badania obrazowego (`imaging_result`). Niezmienny poza potwierdzeniem (`reviewedAt`/`reviewedById`, ustawiane
 * wylacznie przez {@link #acknowledge}); tabela nie ma kolumn audytu, ma tylko `version` (optymistyczne blokowanie,
 * sprawdzane recznie w {@code acknowledge} wzgledem `version` z zadania - patrz {@link ImagingResultService}).
 * `modality`, `examName`, `bodyRegion` i `radiologistName` to snapshoty. FK mapowane skalarnie. Tworzy go
 * {@link ImagingResultRecordingService}.
 */
@Entity
@Table(name = "imaging_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImagingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(name = "modality", nullable = false, updatable = false, length = 15)
    private ImagingModality modality;

    @Column(name = "exam_name", nullable = false, updatable = false, length = 200)
    private String examName;

    @Column(name = "body_region", nullable = false, updatable = false, length = 100)
    private String bodyRegion;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private Instant performedAt;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    @Column(name = "radiologist_name", nullable = false, updatable = false, length = 200)
    private String radiologistName;

    @Column(name = "radiologist_id", updatable = false)
    private UUID radiologistId;

    @Column(name = "technique", updatable = false, columnDefinition = "text")
    private String technique;

    @Column(name = "findings", nullable = false, updatable = false, columnDefinition = "text")
    private String findings;

    @Column(name = "conclusion", nullable = false, updatable = false, columnDefinition = "text")
    private String conclusion;

    @Column(name = "status", nullable = false, updatable = false, length = 15)
    private ImagingResultStatus status;

    @Column(name = "image_count", nullable = false, updatable = false)
    private int imageCount;

    @Column(name = "critical", nullable = false, updatable = false)
    private boolean critical;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by_id")
    private UUID reviewedById;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @SuppressWarnings("java:S107")
    static ImagingResult record(UUID patientId, UUID orderId, ImagingModality modality, String examName,
            String bodyRegion, Instant performedAt, Instant reportedAt, String radiologistName, UUID radiologistId,
            String technique, String findings, String conclusion, ImagingResultStatus status, int imageCount,
            boolean critical) {
        ImagingResult r = new ImagingResult();
        r.patientId = patientId;
        r.orderId = orderId;
        r.modality = modality;
        r.examName = examName;
        r.bodyRegion = bodyRegion;
        r.performedAt = performedAt;
        r.reportedAt = reportedAt;
        r.radiologistName = radiologistName;
        r.radiologistId = radiologistId;
        r.technique = technique;
        r.findings = findings;
        r.conclusion = conclusion;
        r.status = status;
        r.imageCount = imageCount;
        r.critical = critical;
        return r;
    }

    public boolean isAcknowledged() {
        return reviewedAt != null;
    }

    /** Potwierdza wynik; idempotentne - ponowne potwierdzenie nie zmienia kto/kiedy. Zwraca `true`, gdy cos zmieniono. */
    boolean acknowledge(UUID byId, Instant at) {
        if (isAcknowledged()) {
            return false;
        }
        this.reviewedAt = at;
        this.reviewedById = byId;
        return true;
    }
}
