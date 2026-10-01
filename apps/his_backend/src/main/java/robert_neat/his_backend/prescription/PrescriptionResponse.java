package robert_neat.his_backend.prescription;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Prescription z kontraktu (pola opcjonalne pomijane, gdy brak). `status` jest EFEKTYWNY (wygasle "zywe" recepty
 * jako `expired`); `accessCode` to 4 cyfry, `eRxKey` - 44 znaki.
 */
public record PrescriptionResponse(
        UUID id,
        UUID patientId,
        UUID encounterId,
        UUID prescriberId,
        Instant issuedAt,
        LocalDate validFrom,
        LocalDate validUntil,
        PrescriptionKind kind,
        List<PrescriptionItemResponse> items,
        PrescriptionStatus status,
        String accessCode,
        String eRxKey,
        String notes,
        Instant cancelledAt,
        String cancelReason,
        Instant createdAt,
        UUID createdById,
        Instant updatedAt,
        UUID updatedById,
        long version) {
}
