package robert_neat.his_backend.vitals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** `VitalSigns` z kontraktu; pomiary nieobecne w odczycie sa pomijane w JSON (NON_ABSENT), nie `null`. */
public record VitalSignsResponse(
        UUID id,
        UUID patientId,
        Instant recordedAt,
        UUID recordedById,
        VitalContext context,
        VitalSource source,
        String deviceId,
        UUID encounterId,
        Integer systolic,
        Integer diastolic,
        Integer heartRate,
        BigDecimal temperature,
        Integer spo2,
        Integer respiratoryRate,
        Integer painScore,
        String notes) {
}
