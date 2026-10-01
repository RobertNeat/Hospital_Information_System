package robert_neat.his_backend.lab.events;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.common.order.OrderStatus;

/**
 * Zdarzenie domenowe: zmieniono status zlecenia laboratoryjnego (`/status`, `/cancel`, `PUT /fhir/ServiceRequest/{id}` z `actorId` null; publikowane w transakcji;
 * konsument: `alert/AlertEventListener` - synchronicznie w transakcji zrodlowej, alert `order_status` dla
 * `completed`/`cancelled`; `lab/elab/ELabIntegration` przekazuje po commicie anulowanie z aktorem do e-laboratory). Utworzenie zlecenia nie jest zmiana statusu.
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
