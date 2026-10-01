package robert_neat.his_backend.ehr;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `TreatmentStatus` z kontraktu. */
public enum TreatmentStatus implements WireEnum {
    ONGOING("ongoing"),
    COMPLETED("completed"),
    DISCONTINUED("discontinued");

    private final String wire;

    TreatmentStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<TreatmentStatus> {

        public JpaConverter() {
            super(TreatmentStatus.class);
        }
    }
}
