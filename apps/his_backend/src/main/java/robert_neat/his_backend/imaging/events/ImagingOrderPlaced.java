package robert_neat.his_backend.imaging.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Zdarzenie domenowe: utworzono zlecenie badania obrazowego (`ImagingOrderService.create`; publikowane w transakcji;
 * konsument: `imaging/eimg/EImgIntegration` po commicie, gdy integracja jest wlaczona).
 */
public record ImagingOrderPlaced(UUID orderId, UUID patientId, UUID orderedById, Instant placedAt) {
}
