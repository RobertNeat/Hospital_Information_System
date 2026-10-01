package robert_neat.his_backend.patient;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** `EmergencyContact` z kontraktu (`emergency_contact_*`); wszystkie kolumny razem albo wcale. */
@Embeddable
public record EmergencyContact(
        @NotBlank @Size(max = 200) @Column(name = "emergency_contact_full_name", length = 200) String fullName,
        @NotBlank @Size(max = 100) @Column(name = "emergency_contact_relation", length = 100) String relation,
        @NotBlank @Size(max = 30) @Column(name = "emergency_contact_phone", length = 30) String phone,
        @NotNull @Column(name = "emergency_contact_is_legal_guardian") Boolean isLegalGuardian) {
}
