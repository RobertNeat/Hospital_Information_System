package robert_neat.his_backend.lab;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `LabResultStatus` z kontraktu: wstepny, ostateczny, skorygowany. */
public enum ResultStatus implements WireEnum {
    PRELIMINARY("preliminary"),
    FINAL("final"),
    CORRECTED("corrected");

    private final String wire;

    ResultStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    /** Wynik zatwierdzony (`final` lub pozniejsza korekta `corrected`). */
    public boolean isFinalised() {
        return this != PRELIMINARY;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<ResultStatus> {

        public JpaConverter() {
            super(ResultStatus.class);
        }
    }
}
