package robert_neat.his_backend.ehr;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * `Coding` z kontraktu (kolumny `code_system`, `code_value`, `code_display`); jednoczesnie osadzenie w `Diagnosis`
 * oraz DTO zadania i odpowiedzi. Brak lokalnego slownika ICD-10 (usuniety - `icd10_code`); `CodingSystem.ICD_10`
 * jako wynik eksportu/zapisu nadal wystepuje, ale tlumaczenie SCTID -> ICD-10 jest liczone dynamicznie przez
 * Snowstorm Lite (`SnowstormClient#translateToIcd10`), nie odczytywane ze slownika. `display` jest snapshotem
 * (nie zmienia sie wraz ze zrodlem kodu). Zapis diagnoz (`POST /patients/{id}/diagnoses`) i wskazan klinicznych
 * zlecen lab/obrazowych wymaga `system=SNOMED` z poprawnym SCTID (format, bez sprawdzania istnienia pojecia w
 * Snowstorm) - zob. {@link CodingValidation}. Inne wartosci `CodingSystem` sa nadal poprawnym typem na drucie
 * (odczyt starszych danych), ale nie sa akceptowane przy nowym zapisie.
 */
@Embeddable
public record Coding(
        @NotNull @Column(name = "code_system", nullable = false, length = 10) CodingSystem system,
        @NotBlank @Size(max = 30) @Column(name = "code_value", nullable = false, length = 30) String code,
        @NotBlank @Size(max = 500) @Column(name = "code_display", nullable = false, length = 500) String display) {
}
