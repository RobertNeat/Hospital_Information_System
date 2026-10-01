package robert_neat.his_backend.lab.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Zdarzenie domenowe: utworzono zlecenie laboratoryjne (`LabOrderService.create`; publikowane w transakcji;
 * konsument: `lab/elab/ELabIntegration` po commicie, gdy integracja jest wlaczona).
 */
public record LabOrderPlaced(UUID orderId, UUID patientId, UUID orderedById, Instant placedAt) {
}
