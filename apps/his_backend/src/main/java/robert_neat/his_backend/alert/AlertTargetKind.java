package robert_neat.his_backend.alert;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `AlertTargetKind` z kontraktu: typ encji, ktorej dotyczy alert (UI buduje z niego trase). */
public enum AlertTargetKind implements WireEnum {
    LAB_RESULT("lab_result"),
    IMAGING_RESULT("imaging_result"),
    PATIENT_VITALS("patient_vitals"),
    LAB_ORDER("lab_order"),
    IMAGING_ORDER("imaging_order"),
    TASK("task"),
    PATIENT("patient");

    private final String wire;

    AlertTargetKind(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<AlertTargetKind> {

        public JpaConverter() {
            super(AlertTargetKind.class);
        }
    }
}
