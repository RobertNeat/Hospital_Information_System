package robert_neat.his_backend.ehr;

import java.time.Instant;
import java.util.UUID;

/** Contraindication z kontraktu. */
public record ContraindicationResponse(UUID id, UUID patientId, String description, String reason,
        Instant recordedAt) {
}
