package robert_neat.his_backend.alert;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `AlertSeverity` z kontraktu. */
public enum AlertSeverity implements WireEnum {
    INFO("info"),
    WARNING("warning"),
    CRITICAL("critical");

    private final String wire;

    AlertSeverity(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<AlertSeverity> {

        public JpaConverter() {
            super(AlertSeverity.class);
        }
    }
}
