package robert_neat.his_backend.patient;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * `PatientCreateRequest` (= `PatientDraft`) z kontraktu. Pola nadawane przez backend (`id`, `mrn`, `status`,
 * `currentAdmission`, audyt, `version`) sa ignorowane, jesli klient je przysle.
 * Reguly miedzypolowe (PESEL albo `noPeselReason`) sprawdza {@link PatientService}.
 */
public record PatientCreateRequest(
        @Pattern(regexp = "\\d{11}", message = "PESEL musi miec dokladnie 11 cyfr") String pesel,
        NoPeselReason noPeselReason,
        @Valid IdentityDocument identityDocument,
        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) String secondName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull @PastOrPresent LocalDate birthDate,
        @NotNull Gender gender,
        @Size(max = 30) String phone,
        @Size(max = 200) String email,
        @NotNull @Valid Address address,
        @Valid EmergencyContact emergencyContact,
        @NotNull @Valid Insurance insurance,
        BloodType bloodType,
        List<PatientFlag> flags) {
}
