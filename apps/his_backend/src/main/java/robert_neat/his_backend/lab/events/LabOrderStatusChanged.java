package robert_neat.his_backend.lab.events;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.common.order.OrderStatus;

/**
 * Zdarzenie domenowe: zmieniono status zlecenia laboratoryjnego (`/status`, `/cancel`; publikowane w transakcji,
 * bez konsumenta - alerty `order_status` w kolejnych etapach). Utworzenie zlecenia nie jest zmiana statusu.
 */
public record LabOrderStatusChanged(
        UUID orderId,
        UUID patientId,
        UUID orderedById,
        OrderStatus previousStatus,
        OrderStatus status,
        Instant at,
        UUID actorId,
        String note) {
}
