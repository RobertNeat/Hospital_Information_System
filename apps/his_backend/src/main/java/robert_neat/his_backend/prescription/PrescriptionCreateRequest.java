package robert_neat.his_backend.prescription;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * `PrescriptionCreateRequest` z kontraktu. Aktor zawsze z sesji, wiec pole prescriberId nie wystepuje w zadaniu;
 * `patientId` (opcjonalny) musi byc zgodny ze sciezka (422). `accessCode`, `eRxKey`, `status`, `issuedAt` nadaje backend.
 */
public record PrescriptionCreateRequest(
        UUID patientId,
        UUID encounterId,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validUntil,
        @NotNull PrescriptionKind kind,
        @NotEmpty List<@NotNull @Valid PrescriptionItemRequest> items,
        String notes) {
}
