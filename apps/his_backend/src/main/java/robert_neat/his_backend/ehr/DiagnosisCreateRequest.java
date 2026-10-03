package robert_neat.his_backend.ehr;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * DiagnosisCreateRequest z kontraktu. Aktor zawsze z sesji, wiec pole diagnosedById nie wystepuje w zadaniu;
 * brak diagnosedAt = teraz, brak status = active.
 */
public record DiagnosisCreateRequest(
        UUID patientId,
        UUID encounterId,
        @NotNull @Valid Coding code,
        @NotNull DiagnosisType type,
        DiagnosisStatus status,
        Instant diagnosedAt,
        String notes) {
}
