package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `PatientFlag` z kontraktu (`patient_flag.flag`). */
public enum PatientFlag implements WireEnum {
    ISOLATION("isolation"),
    FALL_RISK("fall_risk"),
    DNR("dnr"),
    INFECTION_RISK("infection_risk"),
    VIP("vip");

    private final String wire;

    PatientFlag(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<PatientFlag> {

        public JpaConverter() {
            super(PatientFlag.class);
        }
    }
}
