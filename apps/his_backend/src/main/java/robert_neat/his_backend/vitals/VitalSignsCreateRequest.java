package robert_neat.his_backend.vitals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * `VitalSignsCreateRequest` (= `VitalSignsDraft`) z kontraktu. `recordedById` jest ignorowany (aktor z sesji);
 * `patientId` (opcjonalny) musi byc zgodny ze sciezka (422); `recordedAt` domyslnie "teraz". Pomiary jako liczby
 * dziesietne: calkowitosc i granice `min`/`max` sprawdza {@link VitalsService} (422 z nazwa pola).
 */
public record VitalSignsCreateRequest(
        UUID patientId,
        Instant recordedAt,
        UUID recordedById,
        @NotNull VitalContext context,
        VitalSource source,
        @Size(max = 50) String deviceId,
        UUID encounterId,
        BigDecimal systolic,
        BigDecimal diastolic,
        BigDecimal heartRate,
        BigDecimal temperature,
        BigDecimal spo2,
        BigDecimal respiratoryRate,
        BigDecimal painScore,
        @Size(max = 4000) String notes) {
}
