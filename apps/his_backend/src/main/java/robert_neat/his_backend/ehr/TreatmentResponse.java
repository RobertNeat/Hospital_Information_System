package robert_neat.his_backend.ehr;

import java.time.Instant;
import java.util.UUID;

/** Treatment z kontraktu. */
public record TreatmentResponse(
        UUID id,
        UUID patientId,
        UUID encounterId,
        String name,
        TreatmentType type,
        Instant startAt,
        Instant endAt,
        TreatmentStatus status,
        String description,
        UUID practitionerId) {
}
