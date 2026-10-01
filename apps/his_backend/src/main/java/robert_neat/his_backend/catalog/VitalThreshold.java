package robert_neat.his_backend.catalog;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import robert_neat.his_backend.common.wire.WireEnums;

/** Progi parametru zyciowego (`vital_threshold`, dane referencyjne): normy, progi krytyczne i granice wprowadzania. */
@Entity
@Immutable
@Table(name = "vital_threshold")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VitalThreshold {

    /** Klucz jako tekst: konwerter `autoApply` nie obejmuje atrybutow `@Id`; enum udostepnia {@link #getType()}. */
    @Id
    @Column(name = "type", length = 20)
    private String typeCode;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "low", nullable = false, precision = 6, scale = 1)
    private BigDecimal low;

    @Column(name = "high", nullable = false, precision = 6, scale = 1)
    private BigDecimal high;

    @Column(name = "critical_low", nullable = false, precision = 6, scale = 1)
    private BigDecimal criticalLow;

    @Column(name = "critical_high", nullable = false, precision = 6, scale = 1)
    private BigDecimal criticalHigh;

    @Column(name = "min_value", nullable = false, precision = 6, scale = 1)
    private BigDecimal min;

    @Column(name = "max_value", nullable = false, precision = 6, scale = 1)
    private BigDecimal max;

    public VitalType getType() {
        return WireEnums.fromWire(VitalType.class, typeCode);
    }
}
