package robert_neat.his_backend.lab;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import robert_neat.his_backend.common.order.Urgency;
import robert_neat.his_backend.ehr.Coding;

/**
 * LabOrderCreateRequest z kontraktu. Aktor zawsze z sesji, wiec pole orderedById nie wystepuje w zadaniu;
 * `patientId` (opcjonalny) musi byc zgodny ze sciezka (422).
 */
public record LabOrderCreateRequest(
        UUID patientId,
        UUID encounterId,
        @NotEmpty List<@NotNull @Valid LabOrderItemRequest> items,
        @NotNull Urgency urgency,
        @NotNull Boolean fasting,
        @NotNull Instant plannedCollectionAt,
        @Valid Coding diagnosisCode,
        @NotBlank String clinicalInfo,
        String notes) {
}
