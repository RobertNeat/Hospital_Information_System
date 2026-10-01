package robert_neat.his_backend.ehr;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `DiagnosisType` z kontraktu. */
public enum DiagnosisType implements WireEnum {
    PRIMARY("primary"),
    SECONDARY("secondary"),
    CHRONIC("chronic");

    private final String wire;

    DiagnosisType(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<DiagnosisType> {

        public JpaConverter() {
            super(DiagnosisType.class);
        }
    }
}
