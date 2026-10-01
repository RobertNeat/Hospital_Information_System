package robert_neat.his_backend.catalog;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `ReimbursementLevel` z kontraktu; wartosci na drucie `100%`, `50%`, `30%`, `R`, `B`, `none`. */
public enum ReimbursementLevel implements WireEnum {
    PERCENT_100("100%"),
    PERCENT_50("50%"),
    PERCENT_30("30%"),
    FLAT_RATE("R"),
    FREE("B"),
    NONE("none");

    private final String wire;

    ReimbursementLevel(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<ReimbursementLevel> {

        public JpaConverter() {
            super(ReimbursementLevel.class);
        }
    }
}
