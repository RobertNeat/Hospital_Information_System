package robert_neat.his_backend.imaging.events;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.common.order.OrderStatus;

/**
 * Zdarzenie domenowe: zmieniono status zlecenia obrazowego (`/status`, `/cancel`, automatyczne `completed` po wyniku
 * ostatecznym; publikowane w transakcji; konsument: `alert/AlertEventListener` - synchronicznie w transakcji
 * zrodlowej, alert `order_status` dla `completed`/`cancelled`). Utworzenie
 * zlecenia nie jest zmiana statusu. `actorId` = `null` dla aktora systemowego (auto-`completed` bez sesji).
 */
public record ImagingOrderStatusChanged(
        UUID orderId,
        UUID patientId,
        UUID orderedById,
        OrderStatus previousStatus,
        OrderStatus status,
        Instant at,
        UUID actorId,
        String note) {
}
