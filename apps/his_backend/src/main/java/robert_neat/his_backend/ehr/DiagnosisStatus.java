package robert_neat.his_backend.ehr;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `DiagnosisStatus` z kontraktu. */
public enum DiagnosisStatus implements WireEnum {
    ACTIVE("active"),
    RESOLVED("resolved");

    private final String wire;

    DiagnosisStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<DiagnosisStatus> {

        public JpaConverter() {
            super(DiagnosisStatus.class);
        }
    }
}
