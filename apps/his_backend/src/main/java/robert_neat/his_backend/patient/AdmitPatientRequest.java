package robert_neat.his_backend.patient;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** `AdmitPatientRequest` z kontraktu: dane przyjecia bez pol nadawanych przez backend. */
public record AdmitPatientRequest(
        @NotNull AdmissionType admissionType,
        @NotNull Instant admittedAt,
        @NotNull UUID wardId,
        @Size(max = 20) String room,
        @Size(max = 20) String bed,
        @NotNull UUID attendingPhysicianId,
        TriageLevel triageLevel,
        @NotBlank String reason,
        @Size(max = 50) String referralNumber) {
}
