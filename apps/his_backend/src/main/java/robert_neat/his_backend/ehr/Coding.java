package robert_neat.his_backend.ehr;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * `Coding` z kontraktu (kolumny `code_system`, `code_value`, `code_display`); jednoczesnie osadzenie w `Diagnosis`,
 * DTO zadania i odpowiedzi oraz element slownika ICD-10. `display` jest snapshotem (nie zmienia sie ze slownikiem).
 */
@Embeddable
public record Coding(
        @NotNull @Column(name = "code_system", nullable = false, length = 10) CodingSystem system,
        @NotBlank @Size(max = 30) @Column(name = "code_value", nullable = false, length = 30) String code,
        @NotBlank @Size(max = 500) @Column(name = "code_display", nullable = false, length = 500) String display) {
}
