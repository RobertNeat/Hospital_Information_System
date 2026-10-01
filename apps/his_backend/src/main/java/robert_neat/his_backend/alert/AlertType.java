package robert_neat.his_backend.alert;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `AlertType` z kontraktu. */
public enum AlertType implements WireEnum {
    CRITICAL_RESULT("critical_result"),
    VITAL_ANOMALY("vital_anomaly"),
    ORDER_STATUS("order_status"),
    TASK("task"),
    SYSTEM("system");

    private final String wire;

    AlertType(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<AlertType> {

        public JpaConverter() {
            super(AlertType.class);
        }
    }
}
