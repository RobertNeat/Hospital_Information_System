package robert_neat.his_backend.patient;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * `DischargePatientRequest` z kontraktu. `version` (opcjonalne) to wersja zamykanego, aktywnego przyjecia
 * (`currentAdmission.version`); niezgodnosc = 409.
 */
public record DischargePatientRequest(
        @NotNull Instant dischargedAt,
        DischargeDisposition disposition,
        UUID summaryNoteId,
        Long version) {
}
