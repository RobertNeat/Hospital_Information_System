package robert_neat.his_backend.lab;

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
import robert_neat.his_backend.common.order.OrderStatus;

/** Wpis historii statusow zlecenia (`lab_order_status_change`, tylko dopisywany); wlasciciel: {@link LabOrder}. */
@Entity
@Table(name = "lab_order_status_change")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabOrderStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "status", nullable = false, updatable = false, length = 20)
    private OrderStatus status;

    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;

    @Column(name = "by_id", updatable = false)
    private UUID byId;

    @Column(name = "note", columnDefinition = "text", updatable = false)
    private String note;

    static LabOrderStatusChange of(OrderStatus status, Instant at, UUID byId, String note) {
        LabOrderStatusChange change = new LabOrderStatusChange();
        change.status = status;
        change.at = at;
        change.byId = byId;
        change.note = note;
        return change;
    }
}
