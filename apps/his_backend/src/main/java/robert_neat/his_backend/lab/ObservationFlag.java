package robert_neat.his_backend.lab;

import java.math.BigDecimal;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/**
 * `ResultFlag` z kontraktu: `N` norma, `L`/`H` poza zakresem, `LL`/`HH` krytyczne, `A` nieprawidlowy (wynik tekstowy).
 */
public enum ObservationFlag implements WireEnum {
    N("N"),
    L("L"),
    H("H"),
    LL("LL"),
    HH("HH"),
    A("A");

    private final String wire;

    ObservationFlag(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    public boolean isCritical() {
        return this == LL || this == HH;
    }

    public boolean isAbnormal() {
        return this != N;
    }

    /**
     * Flaga z zakresu referencyjnego: `L` ponizej dolnej, `H` powyzej gornej granicy, w przeciwnym razie `N`.
     * Progi krytyczne (`LL`/`HH`) nie sa czescia katalogu - ustawia je jawnie wprowadzajacy wynik.
     */
    public static ObservationFlag fromRange(BigDecimal value, BigDecimal low, BigDecimal high) {
        if (low != null && value.compareTo(low) < 0) {
            return L;
        }
        if (high != null && value.compareTo(high) > 0) {
            return H;
        }
        return N;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<ObservationFlag> {

        public JpaConverter() {
            super(ObservationFlag.class);
        }
    }
}
