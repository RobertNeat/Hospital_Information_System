package robert_neat.his_backend.lab;

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
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.order.Urgency;
import robert_neat.his_backend.common.persistence.VersionedEntity;
import robert_neat.his_backend.ehr.Coding;

/**
 * Zlecenie laboratoryjne (`lab_order`). Status zmienia sie wylacznie przez {@link #transitionTo} (dopisuje wpis
 * historii); poprawnosc przejscia sprawdza serwis ({@link LabOrderStateMachine}). Pozycje i historia sa wlasnoscia
 * agregatu (kaskada, FK `order_id`). Wyniki (K12) odwoluja sie do zlecenia po stronie `lab_result`.
 */
@Entity
@Table(name = "lab_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabOrder extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "encounter_id", updatable = false)
    private UUID encounterId;

    @Column(name = "ordered_by_id", nullable = false, updatable = false)
    private UUID orderedById;

    @Column(name = "ordered_at", nullable = false, updatable = false)
    private Instant orderedAt;

    @Column(name = "urgency", nullable = false, length = 10)
    private Urgency urgency;

    @Column(name = "fasting", nullable = false)
    private boolean fasting;

    @Column(name = "planned_collection_at", nullable = false)
    private Instant plannedCollectionAt;

    /** Opcjonalna diagnoza: kolumny `diagnosis_code_*` - wszystkie albo zadna. */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "system", column = @Column(name = "diagnosis_code_system", length = 10)),
            @AttributeOverride(name = "code", column = @Column(name = "diagnosis_code_value", length = 30)),
            @AttributeOverride(name = "display", column = @Column(name = "diagnosis_code_display", length = 500))})
    private Coding diagnosisCode;

    @Column(name = "clinical_info", nullable = false, columnDefinition = "text")
    private String clinicalInfo;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @BatchSize(size = 100)
    private List<LabOrderItem> items = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @OrderBy("at ASC, id ASC")
    @BatchSize(size = 100)
    private List<LabOrderStatusChange> statusHistory = new ArrayList<>();

    /** Nowe zlecenie w stanie `ordered` z wpisem poczatkowym historii. */
    static LabOrder place(UUID patientId, UUID encounterId, UUID orderedById, Instant orderedAt, Urgency urgency,
            boolean fasting, Instant plannedCollectionAt, Coding diagnosisCode, String clinicalInfo, String notes,
            List<LabOrderItem> items) {
        LabOrder order = new LabOrder();
        order.patientId = patientId;
        order.encounterId = encounterId;
        order.orderedById = orderedById;
        order.orderedAt = orderedAt;
        order.urgency = urgency;
        order.fasting = fasting;
        order.plannedCollectionAt = plannedCollectionAt;
        order.diagnosisCode = diagnosisCode;
        order.clinicalInfo = clinicalInfo;
        order.notes = notes;
        order.status = OrderStatus.ORDERED;
        order.items.addAll(items);
        order.statusHistory.add(LabOrderStatusChange.of(OrderStatus.ORDERED, orderedAt, orderedById, null));
        return order;
    }

    /** Ustawia status i dopisuje wpis historii (bez sprawdzania dozwolenia przejscia - to robi serwis). */
    void transitionTo(OrderStatus next, Instant at, UUID byId, String note) {
        this.status = next;
        this.statusHistory.add(LabOrderStatusChange.of(next, at, byId, note));
    }
}
