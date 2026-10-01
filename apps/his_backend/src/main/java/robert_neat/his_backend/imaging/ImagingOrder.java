package robert_neat.his_backend.imaging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import robert_neat.his_backend.catalog.ImagingModality;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.order.Urgency;
import robert_neat.his_backend.common.persistence.VersionedEntity;
import robert_neat.his_backend.ehr.Coding;

/**
 * Zlecenie badania obrazowego (`imaging_order`). `examName`, `modality` i `bodyRegion` to snapshoty z katalogu z chwili
 * zlecenia. Status zmienia sie wylacznie przez {@link #transitionTo} (dopisuje wpis historii); poprawnosc przejscia
 * sprawdza serwis ({@link ImagingOrderStateMachine}). Slot grafiku (`slotId`) rezerwuje serwis (kolumna
 * `schedule_slot.available`); unikalnosc gwarantuje czesciowy indeks `uq_imaging_order_slot_id`.
 */
@Entity
@Table(name = "imaging_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImagingOrder extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "encounter_id", updatable = false)
    private UUID encounterId;

    @Column(name = "exam_code", nullable = false, updatable = false, length = 30)
    private String examCode;

    @Column(name = "exam_name", nullable = false, updatable = false, length = 200)
    private String examName;

    @Column(name = "modality", nullable = false, updatable = false, length = 15)
    private ImagingModality modality;

    @Column(name = "body_region", nullable = false, updatable = false, length = 100)
    private String bodyRegion;

    @Column(name = "laterality", nullable = false, updatable = false, length = 10)
    private Laterality laterality;

    @Column(name = "contrast", nullable = false, updatable = false)
    private boolean contrast;

    @Column(name = "clinical_indication", nullable = false, updatable = false, columnDefinition = "text")
    private String clinicalIndication;

    @Column(name = "clinical_question", updatable = false, columnDefinition = "text")
    private String clinicalQuestion;

    /** Opcjonalna diagnoza: kolumny `diagnosis_code_*` - wszystkie albo zadna. */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "system", column = @Column(name = "diagnosis_code_system", length = 10)),
            @AttributeOverride(name = "code", column = @Column(name = "diagnosis_code_value", length = 30)),
            @AttributeOverride(name = "display", column = @Column(name = "diagnosis_code_display", length = 500))})
    private Coding diagnosisCode;

    @Column(name = "urgency", nullable = false, updatable = false, length = 10)
    private Urgency urgency;

    @Embedded
    private SafetyChecklist safety;

    @Column(name = "slot_id", updatable = false)
    private UUID slotId;

    @Column(name = "scheduled_at", updatable = false)
    private Instant scheduledAt;

    @Column(name = "ordered_by_id", nullable = false, updatable = false)
    private UUID orderedById;

    @Column(name = "ordered_at", nullable = false, updatable = false)
    private Instant orderedAt;

    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @OrderBy("at ASC, id ASC")
    @BatchSize(size = 100)
    private List<ImagingOrderStatusChange> statusHistory = new ArrayList<>();

    /**
     * Nowe zlecenie: `ordered` z wpisem poczatkowym, a gdy podano slot - od razu `scheduled` (drugi wpis historii,
     * `scheduledAt` = poczatek slotu).
     */
    static ImagingOrder place(UUID patientId, UUID encounterId, UUID orderedById, Instant orderedAt, String examCode,
            String examName, ImagingModality modality, String bodyRegion, Laterality laterality, boolean contrast,
            String clinicalIndication, String clinicalQuestion, Coding diagnosisCode, Urgency urgency,
            SafetyChecklist safety, UUID slotId, Instant slotStart) {
        ImagingOrder order = new ImagingOrder();
        order.patientId = patientId;
        order.encounterId = encounterId;
        order.orderedById = orderedById;
        order.orderedAt = orderedAt;
        order.examCode = examCode;
        order.examName = examName;
        order.modality = modality;
        order.bodyRegion = bodyRegion;
        order.laterality = laterality;
        order.contrast = contrast;
        order.clinicalIndication = clinicalIndication;
        order.clinicalQuestion = clinicalQuestion;
        order.diagnosisCode = diagnosisCode;
        order.urgency = urgency;
        order.safety = safety;
        order.slotId = slotId;
        order.scheduledAt = slotStart;
        order.status = OrderStatus.ORDERED;
        order.statusHistory.add(ImagingOrderStatusChange.of(OrderStatus.ORDERED, orderedAt, orderedById, null));
        if (slotId != null) {
            order.transitionTo(OrderStatus.SCHEDULED, orderedAt, orderedById, null);
        }
        return order;
    }

    /** Zmiana stanu zainicjowana przez e-imaging (bez uzytkownika HIS; poprawnosc przejscia sprawdza serwis). */
    public void applyExternalStatus(OrderStatus next, Instant at, String note) {
        transitionTo(next, at, null, note);
    }

    /** Ustawia status i dopisuje wpis historii (bez sprawdzania dozwolenia przejscia - to robi serwis). */
    void transitionTo(OrderStatus next, Instant at, UUID byId, String note) {
        this.status = next;
        this.statusHistory.add(ImagingOrderStatusChange.of(next, at, byId, note));
    }
}
