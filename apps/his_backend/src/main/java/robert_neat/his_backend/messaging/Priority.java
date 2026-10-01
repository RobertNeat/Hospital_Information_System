package robert_neat.his_backend.messaging;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `Priority` z kontraktu (wiadomosci i zadania): normal, high, critical. */
public enum Priority implements WireEnum {
    NORMAL("normal"),
    HIGH("high"),
    CRITICAL("critical");

    private final String wire;

    Priority(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<Priority> {

        public JpaConverter() {
            super(Priority.class);
        }
    }
}
