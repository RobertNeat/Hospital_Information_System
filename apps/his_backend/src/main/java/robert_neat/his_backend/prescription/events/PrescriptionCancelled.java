package robert_neat.his_backend.prescription.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Zdarzenie domenowe: anulowano recepte (publikowane w transakcji; konsumenci: `EReceiptIntegration` (tylko z
 * `actorId`, bez odsylania zmian przychodzacych) i `alert/AlertEventListener` (alert `system`/`warning`, niezaleznie
 * od `actorId`)). `actorId` = null oznacza zmiane przychodzaca z e-receipt. `reason` moze byc `null`.
 */
public record PrescriptionCancelled(
        UUID prescriptionId,
        UUID patientId,
        UUID prescriberId,
        UUID actorId,
        String reason,
        Instant cancelledAt) {
}
