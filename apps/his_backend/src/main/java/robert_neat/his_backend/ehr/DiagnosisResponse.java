package robert_neat.his_backend.ehr;

import java.time.Instant;
import java.util.UUID;

/** Diagnosis z kontraktu (audyt opcjonalny - pomijany, gdy brak). */
public record DiagnosisResponse(
        UUID id,
        UUID patientId,
        UUID encounterId,
        Coding code,
        DiagnosisType type,
        DiagnosisStatus status,
        Instant diagnosedAt,
        UUID diagnosedById,
        String notes,
        Instant createdAt,
        UUID createdById,
        Instant updatedAt,
        UUID updatedById,
        long version) {
}
