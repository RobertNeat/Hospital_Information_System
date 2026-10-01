package robert_neat.his_backend.ehr;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `TreatmentType` z kontraktu. */
public enum TreatmentType implements WireEnum {
    PHARMACOTHERAPY("pharmacotherapy"),
    PROCEDURE("procedure"),
    SURGERY("surgery"),
    REHABILITATION("rehabilitation"),
    OTHER("other");

    private final String wire;

    TreatmentType(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<TreatmentType> {

        public JpaConverter() {
            super(TreatmentType.class);
        }
    }
}
