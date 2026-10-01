package robert_neat.his_backend.lab;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import robert_neat.his_backend.catalog.LabCategory;

/**
 * Wynik badania laboratoryjnego (`lab_result`). Niezmienny poza potwierdzeniem (`reviewedAt`/`reviewedById`, ustawiane
 * wylacznie przez {@link #acknowledge}); tabela nie ma kolumn audytu ani `version`. `testName`, `category` i
 * `performerName` to snapshoty z chwili wyniku. FK mapowane skalarnie. Tworzy go {@link LabResultRecordingService}.
 */
@Entity
@Table(name = "lab_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(name = "order_item_id", updatable = false)
    private UUID orderItemId;

    @Column(name = "test_code", nullable = false, updatable = false, length = 30)
    private String testCode;

    @Column(name = "test_name", nullable = false, updatable = false, length = 200)
    private String testName;

    @Column(name = "category", nullable = false, updatable = false, length = 20)
    private LabCategory category;

    @Column(name = "collected_at", nullable = false, updatable = false)
    private Instant collectedAt;

    @Column(name = "resulted_at", nullable = false, updatable = false)
    private Instant resultedAt;

    @Column(name = "status", nullable = false, updatable = false, length = 15)
    private ResultStatus status;

    @Column(name = "performer_name", nullable = false, updatable = false, length = 200)
    private String performerName;

    @Column(name = "comment", columnDefinition = "text", updatable = false)
    private String comment;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by_id")
    private UUID reviewedById;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "result_id", nullable = false, updatable = false)
    @OrderBy("analyteCode ASC, id ASC")
    @BatchSize(size = 100)
    private List<LabObservation> observations = new ArrayList<>();

    static LabResult record(UUID patientId, UUID orderId, UUID orderItemId, String testCode, String testName,
            LabCategory category, Instant collectedAt, Instant resultedAt, ResultStatus status, String performerName,
            String comment, List<LabObservation> observations) {
        LabResult r = new LabResult();
        r.patientId = patientId;
        r.orderId = orderId;
        r.orderItemId = orderItemId;
        r.testCode = testCode;
        r.testName = testName;
        r.category = category;
        r.collectedAt = collectedAt;
        r.resultedAt = resultedAt;
        r.status = status;
        r.performerName = performerName;
        r.comment = comment;
        r.observations.addAll(observations);
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
