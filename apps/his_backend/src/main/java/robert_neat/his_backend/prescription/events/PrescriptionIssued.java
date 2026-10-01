package robert_neat.his_backend.prescription.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import robert_neat.his_backend.prescription.PrescriptionKind;

/**
 * Zdarzenie domenowe: wystawiono recepte (publikowane w transakcji zapisu; konsument: `EReceiptIntegration`, AFTER_COMMIT).
 */
public record PrescriptionIssued(
        UUID prescriptionId,
        UUID patientId,
        UUID prescriberId,
        PrescriptionKind kind,
        LocalDate validFrom,
        LocalDate validUntil,
        int itemCount,
        Instant issuedAt) {
}
