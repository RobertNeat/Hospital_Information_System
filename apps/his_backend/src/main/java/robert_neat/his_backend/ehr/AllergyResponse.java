package robert_neat.his_backend.ehr;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Allergy z kontraktu (audyt i atcCodes opcjonalne - pomijane, gdy brak). */
public record AllergyResponse(
        UUID id,
        UUID patientId,
        String substance,
        AllergyCategory category,
        String reaction,
        AllergySeverity severity,
        AllergyStatus status,
        Instant recordedAt,
        UUID recordedById,
        List<String> atcCodes,
        Instant createdAt,
        UUID createdById,
        Instant updatedAt,
        UUID updatedById,
        long version) {
}
