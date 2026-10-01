package robert_neat.his_backend.prescription;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `TimeOfDay` z kontraktu (pora dnia przyjmowania dawki). */
public enum TimeOfDay implements WireEnum {
    MORNING("morning"),
    NOON("noon"),
    EVENING("evening"),
    NIGHT("night");

    private final String wire;

    TimeOfDay(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<TimeOfDay> {

        public JpaConverter() {
            super(TimeOfDay.class);
        }
    }
}
