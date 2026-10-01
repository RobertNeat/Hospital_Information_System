package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `AdmissionType` z kontraktu. */
public enum AdmissionType implements WireEnum {
    PLANNED("planned"),
    EMERGENCY("emergency"),
    TRANSFER("transfer"),
    OUTPATIENT("outpatient");

    private final String wire;

    AdmissionType(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<AdmissionType> {

        public JpaConverter() {
            super(AdmissionType.class);
        }
    }
}
