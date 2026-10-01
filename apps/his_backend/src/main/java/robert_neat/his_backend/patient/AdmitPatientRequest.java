package robert_neat.his_backend.patient;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * `AdmitPatientRequest` z kontraktu: dane przyjecia bez pol nadawanych przez backend. `wardId`, `attendingPhysicianId`
 * i `reason` sa wymagane poza `outpatient` (sprawdza PatientService).
 */
public record AdmitPatientRequest(
        @NotNull AdmissionType admissionType,
        @NotNull Instant admittedAt,
        UUID wardId,
        @Size(max = 20) String room,
        @Size(max = 20) String bed,
        UUID attendingPhysicianId,
        TriageLevel triageLevel,
        String reason,
        @Size(max = 50) String referralNumber) {
}
