package robert_neat.his_backend.messaging;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `ShiftType` z kontraktu (zmiana dzienna/nocna). */
public enum ShiftType implements WireEnum {
    DAY("day"),
    NIGHT("night");

    private final String wire;

    ShiftType(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<ShiftType> {

        public JpaConverter() {
            super(ShiftType.class);
        }
    }
}
