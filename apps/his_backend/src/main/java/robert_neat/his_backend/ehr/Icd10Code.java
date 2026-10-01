package robert_neat.his_backend.ehr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Slownik ICD-10 (`icd10_code`, dane referencyjne, tylko odczyt). */
@Entity
@Table(name = "icd10_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Icd10Code {

    @Id
    @Column(name = "code", length = 10)
    private String code;

    @Column(name = "display", nullable = false, length = 500)
    private String display;
}
