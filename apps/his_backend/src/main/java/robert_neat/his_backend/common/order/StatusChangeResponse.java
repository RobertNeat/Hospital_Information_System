package robert_neat.his_backend.common.order;

import java.time.Instant;
import java.util.UUID;

/** `StatusChange` z kontraktu (wspolny typ historii statusow zlecen); `byId`, `note` pomijane, gdy brak. */
public record StatusChangeResponse(OrderStatus status, Instant at, UUID byId, String note) {
}
