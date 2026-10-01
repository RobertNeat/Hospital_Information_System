package robert_neat.his_backend.lab;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Obserwacja (analit) wyniku (`lab_observation`, niezmienna). Dokladnie jedna z wartosci: `valueNumeric` albo
 * `valueText` (CHECK w schemacie; w JSON liczba lub string). `analyteName`, `unit` i zakres to snapshot z chwili wyniku.
 * Wlascicielem relacji jest {@link LabResult} (kolumna `result_id`).
 */
@Entity
@Table(name = "lab_observation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "analyte_code", nullable = false, updatable = false, length = 30)
    private String analyteCode;

    @Column(name = "analyte_name", nullable = false, updatable = false, length = 200)
    private String analyteName;

    @Column(name = "value_numeric", updatable = false, precision = 14, scale = 4)
    private BigDecimal valueNumeric;

    @Column(name = "value_text", updatable = false, length = 500)
    private String valueText;

    @Column(name = "unit", nullable = false, updatable = false, length = 30)
    private String unit;

    /** Brak wszystkich trzech kolumn zakresu = `null` (Hibernate); mapper zwraca wtedy pusty zakres. */
    @Embedded
    private ReferenceRange referenceRange;

    @Column(name = "flag", nullable = false, updatable = false, length = 2)
    private ObservationFlag flag;

    static LabObservation of(String analyteCode, String analyteName, BigDecimal valueNumeric, String valueText,
            String unit, ReferenceRange referenceRange, ObservationFlag flag) {
        LabObservation o = new LabObservation();
        o.analyteCode = analyteCode;
        o.analyteName = analyteName;
        o.valueNumeric = valueNumeric;
        o.valueText = valueText;
        o.unit = unit;
        o.referenceRange = referenceRange;
        o.flag = flag;
        return o;
    }
}
