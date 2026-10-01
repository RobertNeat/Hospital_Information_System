package robert_neat.his_backend.patient;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** `Insurance` z kontraktu (`insurance_*`). */
@Embeddable
public record Insurance(
        @NotNull @Column(name = "insurance_status", nullable = false, length = 10) InsuranceStatus status,
        @NotBlank @Size(max = 100) @Column(name = "insurance_nfz_branch", nullable = false, length = 100)
        String nfzBranch,
        @NotNull @Column(name = "insurance_payer", nullable = false, length = 10) InsurancePayer payer,
        @Column(name = "insurance_ewus_verified_at") Instant ewusVerifiedAt) {
}
