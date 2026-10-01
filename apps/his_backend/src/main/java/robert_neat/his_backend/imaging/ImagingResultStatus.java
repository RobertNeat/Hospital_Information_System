package robert_neat.his_backend.imaging;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `ImagingResultStatus` z kontraktu: opis wstepny albo ostateczny. */
public enum ImagingResultStatus implements WireEnum {
    PRELIMINARY("preliminary"),
    FINAL("final");

    private final String wire;

    ImagingResultStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<ImagingResultStatus> {

        public JpaConverter() {
            super(ImagingResultStatus.class);
        }
    }
}
