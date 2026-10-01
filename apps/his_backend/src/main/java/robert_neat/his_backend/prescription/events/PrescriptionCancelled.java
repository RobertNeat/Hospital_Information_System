package robert_neat.his_backend.prescription.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Zdarzenie domenowe: anulowano recepte (publikowane w transakcji; konsument: `EReceiptIntegration`, tylko z `actorId`).
 * `actorId` = null oznacza zmiane przychodzaca z e-receipt (bez odsylania). `reason` moze byc `null`.
 */
public record PrescriptionCancelled(
        UUID prescriptionId,
        UUID patientId,
        UUID prescriberId,
        UUID actorId,
        String reason,
        Instant cancelledAt) {
}
