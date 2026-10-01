package robert_neat.his_backend.patient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * `Patient` z kontraktu (models/patient.model.ts). `pesel` jest zawsze obecny (null = brak numeru, znaczenie
 * semantyczne); pozostale pola null sa pomijane. `currentAdmission` to projekcja aktywnego przyjecia.
 */
public record PatientResponse(
        UUID id,
        String mrn,
        @JsonInclude(JsonInclude.Include.ALWAYS) String pesel,
        NoPeselReason noPeselReason,
        IdentityDocument identityDocument,
        String firstName,
        String secondName,
        String lastName,
        LocalDate birthDate,
        Gender gender,
        String phone,
        String email,
        Address address,
        EmergencyContact emergencyContact,
        Insurance insurance,
        BloodType bloodType,
        PatientStatus status,
        AdmissionResponse currentAdmission,
        List<PatientFlag> flags,
        Instant createdAt,
        Instant updatedAt,
        UUID createdById,
        UUID updatedById,
        long version) {
}
