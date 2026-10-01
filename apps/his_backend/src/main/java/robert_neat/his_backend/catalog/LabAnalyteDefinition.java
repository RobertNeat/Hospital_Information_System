package robert_neat.his_backend.catalog;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Definicja analitu badania (`lab_analyte_definition`, katalog tylko do odczytu): jednostka i zakres referencyjny. */
@Entity
@Immutable
@Table(name = "lab_analyte_definition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabAnalyteDefinition {

    @Id
    private UUID id;

    @Column(name = "test_code", nullable = false, length = 30, insertable = false, updatable = false)
    private String testCode;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "unit", nullable = false, length = 30)
    private String unit;

    @Column(name = "low", precision = 14, scale = 4)
    private BigDecimal low;

    @Column(name = "high", precision = 14, scale = 4)
    private BigDecimal high;
}
